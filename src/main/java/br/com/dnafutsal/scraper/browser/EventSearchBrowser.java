package br.com.dnafutsal.scraper.browser;

import br.com.dnafutsal.scraper.config.ScraperProperties;
import br.com.dnafutsal.scraper.domain.EventSearchCriteria;
import br.com.dnafutsal.scraper.exception.UpstreamAccessException;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;



@Component
public class EventSearchBrowser {

    private static final Logger log = LoggerFactory.getLogger(EventSearchBrowser.class);



    private final ScraperProperties properties;
    private final ReentrantLock browserLock = new ReentrantLock(true);
    private Playwright playwright;
    private Browser browser;


    public EventSearchBrowser(ScraperProperties properties) {
        this.properties = properties;
    }

    public List<Long> search(EventSearchCriteria criteria) {
        if (!properties.browserSearchEnabled()) {
            throw new IllegalStateException(
                    "A pesquisa visual está desabilitada por configuração"
            );
        }

        if (criteria.season() == null) {
            throw new IllegalArgumentException(
                    "A temporada é obrigatória para limitar a pesquisa na fonte externa"
            );
        }

        browserLock.lock();

        try {
            ensureStarted();

            try (BrowserContext context = browser.newContext(
                    new Browser.NewContextOptions()
                            .setUserAgent(properties.userAgent())
                            .setLocale("pt-BR")
            )) {

                Page page = context.newPage();

                page.setDefaultTimeout(
                        properties.requestTimeout().toMillis()
                );

                navigateToSearchPage(page);

                List<Locator> selects = visibleSelects(page);

                if (selects.size() < 4) {
                    throw new UpstreamAccessException(
                            "A estrutura dos filtros do site de origem mudou"
                    );
                }

                /*
                 * Temporada sempre obrigatória.
                 */
                selectApproximate(
                        selects.get(0),
                        String.valueOf(criteria.season())
                );

                waitForDependentOptions(page);

                Set<Long> eventIds = new LinkedHashSet<>();

                /*
                 * Começa pelo nível 1:
                 *
                 * 1 = título
                 * 2 = divisão
                 * 3 = categoria/evento
                 */
                discoverEventIds(
                        page,
                        criteria,
                        1,
                        eventIds
                );

                log.info(
                        "Pesquisa FPFS concluída. criteria={} eventosEncontrados={}",
                        criteria,
                        eventIds.size()
                );

                return new ArrayList<>(eventIds);
            }

        } catch (PlaywrightException exception) {

            log.error(
                    "Erro ao pesquisar eventos na FPFS. criteria={}",
                    criteria,
                    exception
            );

            throw new UpstreamAccessException(
                    "Falha ao executar a pesquisa na página da FPFS",
                    exception
            );

        } finally {
            browserLock.unlock();
        }
    }

    private void navigateToSearchPage(Page page) {
        page.navigate(
                properties.baseUrl(),
                new Page.NavigateOptions()
                        .setWaitUntil(
                                WaitUntilState.DOMCONTENTLOADED
                        )
        );
    }


    private List<Locator> visibleSelects(Page page) {
        Locator all = page.locator("select:visible");
        List<Locator> result = new ArrayList<>();
        for (int index = 0; index < all.count(); index++) {
            result.add(all.nth(index));
        }
        return result;
    }

    private void discoverEventIds(
            Page page,
            EventSearchCriteria criteria,
            int level,
            Set<Long> eventIds
    ) {
        List<Locator> selects = visibleSelects(page);

        if (selects.size() < 4) {
            throw new UpstreamAccessException(
                    "A estrutura dos filtros do site de origem mudou"
            );
        }

        Locator select = selects.get(level);

        String requested =
                requestedValue(criteria, level);

        List<String> values =
                candidateValues(
                        select,
                        requested
                );

        /*
         * Nenhuma opção disponível neste ramo.
         *
         * Isso não é necessariamente erro.
         *
         * Exemplo:
         * determinado campeonato pode não possuir A2.
         */
        if (values.isEmpty()) {
            return;
        }

        for (String value : values) {

            select.selectOption(value);


            if (level == 3) {

                long eventId =
                        parseEventId(value);

                boolean added =
                        eventIds.add(eventId);

                if (added) {
                    log.debug(
                            "Evento FPFS descoberto. eventId={} criteria={}",
                            eventId,
                            criteria
                    );
                }

                continue;
            }

            /*
             * Título ou divisão alterados.
             *
             * Esperamos o próximo select dependente
             * ser atualizado pela página.
             */
            waitForDependentOptions(page);

            discoverEventIds(
                    page,
                    criteria,
                    level + 1,
                    eventIds
            );
        }
    }

    private long parseEventId(String value) {
        try {
            long eventId =
                    Long.parseLong(value);

            if (eventId <= 0) {
                throw new NumberFormatException(
                        "Event ID deve ser positivo"
                );
            }

            return eventId;

        } catch (NumberFormatException exception) {

            throw new UpstreamAccessException(
                    "O identificador de evento retornado pela FPFS é inválido: "
                            + value,
                    exception
            );
        }
    }

    private String requestedValue(
            EventSearchCriteria criteria,
            int level
    ) {
        return switch (level) {
            case 1 -> criteria.title();
            case 2 -> criteria.division();
            case 3 -> criteria.category();

            default -> throw new IllegalArgumentException(
                    "Nível de filtro inválido: " + level
            );
        };
    }

    private List<String> candidateValues(
            Locator select,
            String requested
    ) {
        /*
         * Nenhum filtro informado:
         *
         * percorremos TODAS as opções válidas.
         */
        if (!hasText(requested)) {
            return availableOptionValues(select);
        }

        /*
         * Filtro informado:
         *
         * procuramos apenas a opção correspondente.
         */
        String value =
                findApproximateOptionValue(
                        select,
                        requested
                );

        if (value == null) {
            return List.of();
        }

        return List.of(value);
    }

    private List<String> availableOptionValues(
            Locator select
    ) {
        Object result = select.evaluate("""
            select => Array.from(select.options)
                .filter(option => {
                    const value =
                        String(option.value || '').trim();

                    const text =
                        String(option.textContent || '').trim();

                    return !option.disabled
                        && value !== ''
                        && value !== '0'
                        && value !== '-1'
                        && !/selecione/i.test(text)
                        && !/escolha/i.test(text);
                })
                .map(option => option.value)
            """);

        if (!(result instanceof List<?> values)) {
            return List.of();
        }

        return values.stream()
                .map(String::valueOf)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private String findApproximateOptionValue(
            Locator select,
            String requestedText
    ) {
        Object value = select.evaluate("""
            (select, requested) => {

              const normalize = value =>
                String(value || '')
                  .normalize('NFD')
                  .replace(/[\\u0300-\\u036f]/g, '')
                  .toLowerCase()
                  .replace(/[^a-z0-9]+/g, ' ')
                  .replace(/\\s+/g, ' ')
                  .trim();

              const target = normalize(requested);

              const options =
                Array.from(select.options)
                  .filter(option => {
                    const value =
                        String(option.value || '').trim();

                    const text =
                        String(option.textContent || '').trim();

                    return !option.disabled
                        && value !== ''
                        && value !== '0'
                        && value !== '-1'
                        && !/selecione/i.test(text)
                        && !/escolha/i.test(text);
                  });

              const exact =
                options.find(
                  option =>
                    normalize(option.textContent) === target
                );

              const partial =
                options.find(
                  option =>
                    normalize(option.textContent)
                      .includes(target)
                );

              const reverse =
                options.find(option => {
                  const normalized =
                    normalize(option.textContent);

                  return normalized.length > 1
                    && target.includes(normalized);
                });

              return (
                exact
                || partial
                || reverse
                || {}
              ).value || null;
            }
            """, requestedText);

        if (value instanceof String result
                && !result.isBlank()) {

            return result;
        }

        return null;
    }



    private void selectApproximate(
            Locator select,
            String requestedText
    ) {
        String optionValue =
                findApproximateOptionValue(
                        select,
                        requestedText
                );

        if (optionValue == null) {
            throw new IllegalArgumentException(
                    "Filtro não encontrado no site de origem: "
                            + requestedText
            );
        }

        select.selectOption(optionValue);
    }

    private void waitForDependentOptions(Page page) {
        page.waitForTimeout(650);
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(4_000));
        } catch (PlaywrightException ignored) {
            // Alguns ‘scripts’ mantêm conexões abertas; o atraso acima permite popular os selects.
        }
    }


    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void ensureStarted() {
        if (browser != null) {
            return;
        }
        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        } catch (PlaywrightException exception) {
            closeResources();
            throw new IllegalStateException(
                    "Chromium do Playwright não está instalado. Execute: "
                            + "mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI "
                            + "-Dexec.args=\"install --with-deps chromium\"",
                    exception
            );
        }
    }

    @PreDestroy
    void stop() {
        closeResources();
    }

    private void closeResources() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
    }

}
