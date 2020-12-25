package io.github.karlatemp.mxlib.selenium;

import com.google.common.base.Splitter;
import io.github.karlatemp.mxlib.MxLib;
import io.github.karlatemp.mxlib.common.utils.IOUtils;
import io.github.karlatemp.mxlib.utils.Toolkit;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

import static org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class MxSelenium {
    static final Charset STD_CHARSET;
    static final File JAVA_EXECUTABLE;
    static final File data = new File(MxLib.getDataStorage(), "selenium");
    static final HttpClient client = HttpClientBuilder.create().build();
    static boolean IS_SUPPORT;

    public static boolean isSupported() {
        return IS_SUPPORT;
    }

    static {
        {
            File je;
            String sbt = System.getProperty("sun.boot.library.path");
            File exec_windows = new File(sbt, "java.exe");
            File exec = new File(sbt, "java");
            if (exec_windows.isFile()) {
                je = exec_windows;
            } else if (exec.exists() && exec.canExecute()) {
                je = exec;
            } else {
                String jhome = System.getProperty("java.home");
                exec_windows = new File(jhome, "bin/java.exe");
                exec = new File(jhome, "bin/java");
                if (exec_windows.isFile()) {
                    je = exec_windows;
                } else if (exec.exists() && exec.canExecute()) {
                    je = exec;
                } else {
                    je = null;
                }
            }
            JAVA_EXECUTABLE = je;
        }
        {
            Charset c;
            try {
                String property = System.getProperty("sun.stdout.encoding");
                if (property == null) {
                    if (JAVA_EXECUTABLE == null) {
                        throw new UnsupportedOperationException("Java Executable not found.");
                    }
                    try {
                        String result = commandProcessResult(true, JAVA_EXECUTABLE.getPath(), "-XshowSettings:properties", "-version");
                        Optional<String> fileEncoding = Splitter.on('\n').splitToList(result).stream()
                                .filter(it -> it.trim().startsWith("file.encoding"))
                                .findFirst();
                        String rex = fileEncoding.orElse(null);
                        if (rex != null) {
                            property = Splitter.on('=').limit(2)
                                    .splitToList(rex)
                                    .get(1)
                                    .trim();
                        }
                    } catch (Throwable ignore) {
                    }
                }
                //noinspection ConstantConditions
                c = Charset.forName(property);
            } catch (Throwable ignored) {
                try {
                    c = Charset.forName(System.getProperty("file.encoding"));
                } catch (Throwable ignored0) {
                    c = Charset.defaultCharset();
                }
            }
            STD_CHARSET = c;
        }
        data.mkdirs();
    }

    static String commandProcessResult(String... cmd) throws IOException {
        return commandProcessResult(false, cmd);
    }

    static String commandProcessResult(boolean errorStream, String... cmd) throws IOException {
        Process process = new ProcessBuilder(cmd).start();
        return new String(IOUtils.readAllBytes(
                errorStream ? process.getErrorStream() : process.getInputStream()
        ), STD_CHARSET == null ? Charset.defaultCharset() : STD_CHARSET);
    }

    private static boolean initialized;
    private static BiFunction<String, Consumer<Capabilities>, RemoteWebDriver> driverSupplier;

    private static void initialize() throws Exception {
        if (initialized) return;
        synchronized (MxSelenium.class) {
            if (initialized) return;
            initialized = true;
            String os = System.getProperty("os.name");
            if (os.toLowerCase().startsWith("windows ")) {
                String browser = WindowsKit.queryBrowserUsing();
                if (browser.startsWith("Chrome")) {
                    // region chrome
                    File bat = new File(data, "chromever.bat");
                    if (!bat.isFile()) {
                        data.mkdirs();
                        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(bat));
                             InputStream res = MxSelenium.class.getResourceAsStream("resources/chromever.bat")) {
                            Toolkit.IO.writeTo(res, out);
                        }
                    }
                    String result = commandProcessResult("cmd", "/c", bat.getPath());
                    Map<String, Map<String, String>> chromever = WindowsKit.parseRegResult(result);

                    Map<String, String> next = chromever.values().iterator().next();
                    String ver = next.getOrDefault("opv", next.get("pv"));

                    File chromedriverExecutable = new File(data, "chromedriver-" + ver + ".exe");

                    if (!chromedriverExecutable.isFile()) {
                        String url = "https://chromedriver.storage.googleapis.com/" + ver + "/chromedriver_win32.zip";
                        File chromedriverZip = new File(data, "chromedriver-" + ver + ".zip");
                        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(chromedriverZip))) {
                            client.execute(new HttpGet(url)).getEntity().writeTo(out);
                        }
                        try (ZipFile zip = new ZipFile(chromedriverZip)) {
                            try (InputStream inp = zip.getInputStream(zip.getEntry("chromedriver.exe"));
                                 OutputStream out = new BufferedOutputStream(new FileOutputStream(chromedriverExecutable))) {
                                Toolkit.IO.writeTo(inp, out);
                            }
                        }
                    }
                    System.setProperty("webdriver.chrome.driver", chromedriverExecutable.getPath());
                    driverSupplier = (agent, c) -> {
                        ChromeOptions options = new ChromeOptions();
                        if (agent != null) options.addArguments("user-agent=" + agent);
                        if (c != null) c.accept(options);
                        return new ChromeDriver(options);
                    };
                    IS_SUPPORT = true;
                    // endregion
                } else if (browser.toLowerCase().startsWith("firefox")) {
                    FirefoxKit.fetch();
                    File provider = FirefoxKit.parse();
                    System.setProperty("webdriver.gecko.driver", provider.getPath());
                    driverSupplier = firefox();
                    IS_SUPPORT = true;
                } else {
                    driverSupplier = (agent, c) -> {
                        throw new UnsupportedOperationException("Unsupported browser: " + browser + ", Only chrome/firefox supportted");
                    };
                    IS_SUPPORT = false;
                }
            } else if (os.equals("Linux")) {
                try {
                    String type = commandProcessResult("xdg-settings", "get", "default-web-browser");
                    if (type.toLowerCase().startsWith("firefox")) {
                        FirefoxKit.fetch();
                        File provider = FirefoxKit.parse();
                        System.setProperty("webdriver.gecko.driver", provider.getPath());
                        driverSupplier = firefox();
                        IS_SUPPORT = true;
                    } else {
                        driverSupplier = (agent, c) -> {
                            throw new UnsupportedOperationException("Unsupported Platform: " + os + ", " + type + ", Only FireFox browser supportted now.");
                        };
                        IS_SUPPORT = false;
                    }
                } catch (Throwable ignored) {
                }
            }
            if (driverSupplier == null) {
                driverSupplier = (agent, c) -> {
                    throw new UnsupportedOperationException("Unsupported Platform: " + os);
                };
                IS_SUPPORT = false;
            }
        }
    }

    private static BiFunction<String, Consumer<Capabilities>, RemoteWebDriver> firefox() {
        return (agent, c) -> {
            FirefoxOptions options = new FirefoxOptions();
            if (agent != null) options.addPreference("general.useragent.override", agent);
            if (c != null) c.accept(options);
            return new FirefoxDriver(options);
        };
    }

    public static RemoteWebDriver newDriver() {
        return newDriver(null);
    }

    public static RemoteWebDriver newDriver(String useragent) {
        return newDriver(useragent, null);
    }

    public static RemoteWebDriver newDriver(String useragent, Consumer<Capabilities> consumer) {
        try {
            initialize();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return driverSupplier.apply(useragent, consumer);
    }

    public static void main(String[] args) throws Throwable {
        WebDriver driver = newDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10000).getSeconds());
        driver.get("https://google.com/ncr");
        driver.findElement(By.name("q")).sendKeys("cheese" + Keys.ENTER);
        WebElement firstResult = wait.until(presenceOfElementLocated(By.cssSelector("h3>div")));
        System.out.println(firstResult.getAttribute("textContent"));
        driver.quit();
    }
}
