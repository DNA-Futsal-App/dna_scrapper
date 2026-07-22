package br.com.dnafutsal.scraper.browser;

import br.com.dnafutsal.scraper.config.ScraperProperties;
import br.com.dnafutsal.scraper.domain.EventSearchCriteria;
import br.com.dnafutsal.scraper.exception.UpstreamAccessException;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class EventSearchBrowser {

    private static final Pattern EVENT_ID = Pattern.compile("/evento/(\\d+)(?:/|$)");

    private final ScraperProperties properties;
    private final ReentrantLock browserLock = new ReentrantLock(true);
    private Playwright playwright;
    private Browser browser;

    public EventSearchBrowser(ScraperProperties properties) {
        this.properties = properties;
    }

    public List<Long> search(EventSearchCriteria criteria) {
        if (!properties.browserSearchEnabled()) {
            throw new IllegalStateException("A pesquisa visual está desabilitada por configuração");
        }
        if (criteria.season() == null) {
            throw new IllegalArgumentException("A temporada é obrigatória para limitar a pesquisa na fonte externa");
        }

        browserLock.lock();
        try {
            ensureStarted();
            try (BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent(properties.userAgent())
                    .setLocale("pt-BR"))) {
                Page page = context.newPage();
                page.setDefaultTimeout(properties.requestTimeout().toMillis());
                page.navigate(
                        properties.baseUrl(),
                        new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                );

                List<Locator> selects = visibleSelects(page);
                if (selects.size() < 4) {
                    throw new UpstreamAccessException("A estrutura dos filtros do site de origem mudou");
                }

                selectApproximate(selects.get(0), String.valueOf(criteria.season()));
                waitForDependentOptions(page);
                if (hasText(criteria.title())) {
                    selectApproximate(selects.get(1), criteria.title());
                    waitForDependentOptions(page);
                }
                if (hasText(criteria.division())) {
                    selectApproximate(selects.get(2), criteria.division());
                    waitForDependentOptions(page);
                }
                if (hasText(criteria.category())) {
                    selectApproximate(selects.get(3), criteria.category());
                    waitForDependentOptions(page);
                }

                Locator searchButton = page.locator(
                        "button:has-text('Buscar'):visible, "
                                + "input[type='submit'][value*='Buscar']:visible, "
                                + "a:has-text('Buscar'):visible"
                ).first();
                if (searchButton.count() == 0) {
                    throw new UpstreamAccessException("O botão Buscar não foi encontrado no site de origem");
                }
                searchButton.click();
                waitForResults(page);

                Set<Long> ids = new LinkedHashSet<>();
                addEventId(page.url(), ids);
                @SuppressWarnings("unchecked")
                List<String> hrefs = (List<String>) page.locator("a[href*='/evento/']")
                        .evaluateAll("els => els.map(e => e.href)");
                hrefs.forEach(href -> addEventId(href, ids));
                return new ArrayList<>(ids);
            }
        } catch (PlaywrightException exception) {
            throw new UpstreamAccessException("Falha ao executar a pesquisa na página da FPFS", exception);
        } finally {
            browserLock.unlock();
        }
    }

    private List<Locator> visibleSelects(Page page) {
        Locator all = page.locator("select:visible");
        List<Locator> result = new ArrayList<>();
        for (int index = 0; index < all.count(); index++) {
            result.add(all.nth(index));
        }
        return result;
    }

    private void selectApproximate(Locator select, String requestedText) {
        Object value = select.evaluate("""
                (select, requested) => {
                  const normalize = value => value
                    .normalize('NFD')
                    .replace(/[\\u0300-\\u036f]/g, '')
                    .toLowerCase()
                    .replace(/[^a-z0-9]+/g, ' ')
                    .replace(/\\s+/g, ' ')
                    .trim();
                  const target = normalize(requested);
                  const options = Array.from(select.options);
                  const exact = options.find(option => normalize(option.text) === target);
                  const partial = options.find(option => normalize(option.text).includes(target));
                  const reverse = options.find(option =>
                    target.includes(normalize(option.text)) && normalize(option.text).length > 1
                  );
                  return (exact || partial || reverse || {}).value || null;
                }
                """, requestedText);

        if (!(value instanceof String optionValue) || optionValue.isBlank()) {
            throw new IllegalArgumentException("Filtro não encontrado no site de origem: " + requestedText);
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

    private void waitForResults(Page page) {
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(8_000));
        } catch (PlaywrightException ignored) {
            page.waitForTimeout(1_000);
        }
    }

    private void addEventId(String url, Set<Long> ids) {
        Matcher matcher = EVENT_ID.matcher(url == null ? "" : url);
        while (matcher.find()) {
            ids.add(Long.parseLong(matcher.group(1)));
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
