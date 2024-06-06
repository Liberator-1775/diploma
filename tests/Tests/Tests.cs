using OpenQA.Selenium;
using OpenQA.Selenium.Chrome;
using OpenQA.Selenium.Support.UI;

namespace Tests;

[TestFixture]
public class Tests
{
    private ChromeDriver _driver;
    private WebDriverWait _wait;
    private string _extensionId = string.Empty;
    
    [OneTimeSetUp]
    public void OneTimeSetUp()
    {
        Directory.SetCurrentDirectory("../../../../../chrome");
        var options = new ChromeOptions();
        options.AddExtension("../chrome.crx");
        _driver = new ChromeDriver(options);
        _wait = new WebDriverWait(_driver, TimeSpan.FromSeconds(30));
        _driver.Navigate().GoToUrl("chrome://extensions/");
        var developerToggle = (WebElement)_driver.ExecuteScript(
            "return arguments[0].shadowRoot.getElementById(\"toolbar\").shadowRoot.getElementById(\"devMode\")",
            _wait.Until(driver => driver.FindElement(By.TagName("extensions-manager"))));
        developerToggle.Click();
        var itemsContainer = (WebElement)_driver.ExecuteScript(
            "return arguments[0].shadowRoot.getElementById(\"viewManager\").getElementsByClassName(\"active\")[0].shadowRoot.getElementById(\"content-wrapper\").getElementsByClassName(\"items-container\")[1]",
            _wait.Until(driver => driver.FindElement(By.TagName("extensions-manager"))));
        var extensionId = itemsContainer.FindElement(By.TagName("extensions-item"));
        _extensionId = extensionId.GetAttribute("id");
        _driver.Dispose();
    }
    
    [SetUp]
    public void SetUp()
    {
        var options = new ChromeOptions();
        options.AddExtension("../chrome.crx");
        _driver = new ChromeDriver(options);
        _wait = new WebDriverWait(_driver, TimeSpan.FromSeconds(20));
        _driver.Navigate().GoToUrl($"chrome-extension://{_extensionId}/popup.html");
    }
    
    [Test]
    public void EnsureOnlyGoogleInputIsVisible()
    {
        var elements = _wait.Until(driver => driver.FindElements(By.ClassName("hidden")));
        Assert.That(elements, Has.Count.EqualTo(5));
    }

    [Test]
    public void EnsureErrorAppearsAfterWrongGoogleInput()
    {
        var googleInput = _wait.Until(driver => driver.FindElement(By.Id("googleApiKey")));
        var googleSubmitButton = _wait.Until(driver => driver.FindElement(By.Id("googleSubmit")));
        googleInput.SendKeys("1111");
        googleSubmitButton.Click();
        Assert.DoesNotThrow(() => _wait.Until(driver => driver.FindElement(By.ClassName("alert")).Displayed));
    }

    [Test]
    public void EnsureDeepLInputAndSpeakingRateAppearsAndGoogleInputDisappearsAfterCorrectGoogleInput()
    {
        var googleInput = _wait.Until(driver => driver.FindElement(By.Id("googleApiKey")));
        var googleSubmitButton = _wait.Until(driver => driver.FindElement(By.Id("googleSubmit")));
        googleInput.SendKeys("{googleApiKey}");
        googleSubmitButton.Click();
        Assert.DoesNotThrow(() => _wait.Until(driver => driver.FindElement(By.Id("deeplSection")).Displayed));
        Assert.DoesNotThrow(() => _wait.Until(driver => driver.FindElement(By.Id("speedSection")).Displayed));
        Assert.DoesNotThrow(() => _wait.Until(driver => driver.FindElement(By.Id("googleSection")).Displayed == false));
    }

    [Test]
    public void EnsureErrorAppearsAfterWrongDeepLInput()
    {
        EnsureDeepLInputAndSpeakingRateAppearsAndGoogleInputDisappearsAfterCorrectGoogleInput();
        var deeplInput = _wait.Until(driver => driver.FindElement(By.Id("deeplApiKey")));
        var deeplSubmitButton = _wait.Until(driver => driver.FindElement(By.Id("deeplSubmit")));
        deeplInput.SendKeys("1111");
        deeplSubmitButton.Click();
        Assert.DoesNotThrow(() => _wait.Until(driver => driver.FindElement(By.ClassName("alert")).Displayed));
    }

    [Test]
    public void EnsureSwitchAppearsAndDeepLInputDisappearsAfterCorrectDeepLInput()
    {
        EnsureDeepLInputAndSpeakingRateAppearsAndGoogleInputDisappearsAfterCorrectGoogleInput();
        var deeplInput = _wait.Until(driver => driver.FindElement(By.Id("deeplApiKey")));
        var deeplSubmitButton = _wait.Until(driver => driver.FindElement(By.Id("deeplSubmit")));
        deeplInput.SendKeys("{deeplApiKey}");
        deeplSubmitButton.Click();
        Assert.DoesNotThrow(() => _wait.Until(driver => driver.FindElement(By.Id("translationSection")).Displayed));
        Assert.DoesNotThrow(() => _wait.Until(driver => driver.FindElement(By.Id("deeplSection")).Displayed == false));
    }

    [Test]
    public void EnsureSelectAppearsAfterSwitchOn()
    {
        EnsureSwitchAppearsAndDeepLInputDisappearsAfterCorrectDeepLInput();
        var translationSwitch = _wait.Until(driver => driver.FindElement(By.Id("translationToggle")));
        translationSwitch.Click();
        Assert.DoesNotThrow(() => _wait.Until(driver => driver.FindElement(By.Id("languageSection")).Displayed));
    }

    [Test]
    public void EnsureLanguageIsRussianAfterChangingLanguageAndTurningSwitchOff()
    {
        EnsureSelectAppearsAfterSwitchOn();
        var select = _wait.Until(driver => driver.FindElement(By.Id("targetLanguage")));
        select.Click();
        var englishOption = _wait.Until(_ => select.FindElement(By.XPath("//*[@id=\"targetLanguage\"]/option[2]")));
        englishOption.Click();
        var translationSwitch = _wait.Until(driver => driver.FindElement(By.Id("translationToggle")));
        translationSwitch.Click();
        translationSwitch.Click();
        Assert.That(new SelectElement(select).SelectedOption.Text, Is.EqualTo("Russian"));
    }

    [TearDown]
    public void TearDown()
    {
        _driver.Dispose();
    }

    [OneTimeTearDown]
    public void OneTimeTearDown()
    {
        _driver.Dispose();
    }
}