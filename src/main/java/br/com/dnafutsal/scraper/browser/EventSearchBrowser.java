package br.com.dnafutsal.scraper.browser;

import br.com.dnafutsal.scraper.config.ScraperProperties;
import br.com.dnafutsal.scraper.domain.CatalogEntry;
import br.com.dnafutsal.scraper.domain.EventSearchCriteria;
import br.com.dnafutsal.scraper.exception.UpstreamAccessException;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;



@Component
public class EventSearchBrowser {

    private static final Logger log = LoggerFactory.getLogger(EventSearchBrowser.class);



    private final ScraperProperties properties;
    private final ReentrantLock browserLock = new ReentrantLock(true);
    private Playwright playwright;
    private Browser browser;

    private static final String CAMPEONATO_PAULISTA = "Campeonato Paulista";

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




    public List<CatalogEntry> catalog(int season) {
        if (!properties.browserSearchEnabled()) {
            throw new IllegalStateException(
                    "A pesquisa visual está desabilitada por configuração"
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

                page.navigate(
                        properties.baseUrl(),
                        new Page.NavigateOptions()
                                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                );

                List<Locator> selects = visibleSelects(page);

                if (selects.size() < 4) {
                    throw new UpstreamAccessException(
                            "A estrutura dos filtros do site de origem mudou"
                    );
                }

                Locator seasonSelect = selects.get(0);
                Locator titleSelect = selects.get(1);
                Locator divisionSelect = selects.get(2);
                Locator categorySelect = selects.get(3);

                /*
                 * 1. Seleciona somente a temporada desejada.
                 */
                selectApproximate(
                        seasonSelect,
                        String.valueOf(season)
                );

                /*
                 * Aguarda os títulos serem carregados.
                 */
                waitForSelectOptions(
                        page,
                        titleSelect,
                        "Título"
                );

                /*
                 * 2. Seleciona especificamente Campeonato Paulista.
                 *
                 * Não percorremos todos os campeonatos.
                 */
                selectApproximate(
                        titleSelect,
                        CAMPEONATO_PAULISTA
                );

                /*
                 * Aguarda as divisões do Campeonato Paulista.
                 */
                waitForSelectOptions(
                        page,
                        divisionSelect,
                        "Divisão"
                );

                /*
                 * Fazemos uma cópia porque esse select poderá sofrer
                 * alterações no DOM durante a navegação.
                 */
                List<SelectOption> divisions =
                        List.copyOf(options(divisionSelect));

                log.info(
                        "Divisões encontradas no Campeonato Paulista. season={} total={}",
                        season,
                        divisions.size()
                );

                Map<String, CatalogEntry> entries =
                        new LinkedHashMap<>();


                for (SelectOption division : divisions) {


                    String previousCategorySignature =
                            optionSignature(categorySelect);

                    selectByValue(
                            divisionSelect,
                            division.value()
                    );


                    waitForSelectOptionsChanged(
                            page,
                            categorySelect,
                            previousCategorySignature,
                            "Categoria da divisão " + division.label()
                    );

                    List<SelectOption> categories =
                            List.copyOf(options(categorySelect));

                    log.debug(
                            "Categorias encontradas. division={} total={}",
                            division.label(),
                            categories.size()
                    );

                    for (SelectOption category : categories) {

                        CatalogEntry entry =
                                new CatalogEntry(
                                        CAMPEONATO_PAULISTA,
                                        division.label(),
                                        category.label()
                                );

                        entries.putIfAbsent(
                                catalogKey(entry),
                                entry
                        );
                    }
                }

                List<CatalogEntry> result =
                        List.copyOf(entries.values());

                log.info(
                        "Catálogo do Campeonato Paulista concluído. season={} divisions={} entries={}",
                        season,
                        divisions.size(),
                        result.size()
                );

                return result;
            }

        } catch (PlaywrightException exception) {

            log.error(
                    "Falha Playwright ao ler catálogo do Campeonato Paulista. season={}",
                    season,
                    exception
            );

            throw new UpstreamAccessException(
                    "Falha ao ler os filtros na página da FPFS",
                    exception
            );

        } finally {
            browserLock.unlock();
        }
    }

    private void waitForSelectOptions(
            Page page,
            Locator select,
            String selectName
    ) {
        long timeout =
                Math.min(
                        properties.requestTimeout().toMillis(),
                        8_000
                );

        long deadline =
                System.currentTimeMillis() + timeout;

        PlaywrightException lastException = null;

        while (System.currentTimeMillis() < deadline) {
            try {
                List<SelectOption> available =
                        options(select);

                if (!available.isEmpty()) {
                    return;
                }

            } catch (PlaywrightException exception) {
                lastException = exception;
            }

            page.waitForTimeout(100);
        }

        if (lastException != null) {
            throw new UpstreamAccessException(
                    "Falha ao aguardar opções do filtro: "
                            + selectName,
                    lastException
            );
        }

        throw new UpstreamAccessException(
                "Timeout aguardando opções do filtro: "
                        + selectName
        );
    }

    private void waitForSelectOptionsChanged(
            Page page,
            Locator select,
            String previousSignature,
            String selectName
    ) {
        long timeout =
                Math.min(
                        properties.requestTimeout().toMillis(),
                        8_000
                );

        long deadline =
                System.currentTimeMillis() + timeout;

        while (System.currentTimeMillis() < deadline) {

            String currentSignature =
                    optionSignature(select);

            /*
             * O select:
             *
             * 1. precisa possuir opções válidas
             * 2. precisa ser diferente do estado anterior
             */
            if (!currentSignature.isBlank()
                    && !currentSignature.equals(previousSignature)) {

                /*
                 * Pequena confirmação de estabilidade.
                 *
                 * Evita ler o DOM no meio da atualização.
                 */
                page.waitForTimeout(150);

                String confirmedSignature =
                        optionSignature(select);

                if (currentSignature.equals(confirmedSignature)) {
                    return;
                }
            }

            page.waitForTimeout(100);
        }

        throw new UpstreamAccessException(
                "Timeout aguardando atualização do filtro: "
                        + selectName
        );
    }

    private String optionSignature(Locator select) {
        List<SelectOption> values =
                options(select);

        if (values.isEmpty()) {
            return "";
        }

        return values.stream()
                .map(option ->
                        option.value()
                                + ":"
                                + option.label()
                )
                .sorted()
                .reduce(
                        (left, right) ->
                                left + "|" + right
                )
                .orElse("");
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





    @SuppressWarnings("unchecked")

    private List<SelectOption> options(Locator select) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawOptions = (List<Map<String, Object>>) select.evaluate("""
                select => Array.from(select.options).map(option => ({
                  value: option.value || '',
                  label: option.textContent || ''
                }))
                """);

        List<SelectOption> result = new ArrayList<>();
        for (Map<String, Object> rawOption : rawOptions) {
            SelectOption option = new SelectOption(
                    String.valueOf(rawOption.getOrDefault("value", "")).trim(),
                    String.valueOf(rawOption.getOrDefault("label", "")).trim()
            );
            if (isRealOption(option)) {
                result.add(option);
            }
        }
        return result;
    }

    private void selectByValue(Locator select, String value) {
        select.selectOption(value);
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

    private boolean isRealOption(SelectOption option) {
        String normalized = normalize(option.label());
        return !option.value().isBlank()
                && !normalized.isBlank()
                && !normalized.equals("todos")
                && !normalized.equals("todas")
                && !normalized.equals("selecione")
                && !normalized.startsWith("selecione ");
    }

    private String catalogKey(CatalogEntry entry) {
        return normalize(entry.title()) + "|" + normalize(entry.division()) + "|" + normalize(entry.category());
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
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

    private record SelectOption(String value, String label) {
    }
}
