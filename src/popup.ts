import { waitForElm } from "./utils";

const googleApiKeyInput = (await waitForElm(
  "#googleApiKey",
)) as HTMLInputElement;
const googleSubmitButton = (await waitForElm(
  "#googleSubmit",
)) as HTMLButtonElement;
const deeplApiKeyInput = (await waitForElm("#deeplApiKey")) as HTMLInputElement;
const deeplSubmitButton = (await waitForElm(
  "#deeplSubmit",
)) as HTMLButtonElement;
const speakingRateInput = (await waitForElm(
  "#speakingRate",
)) as HTMLInputElement;
const translationToggle = (await waitForElm(
  "#translationToggle",
)) as HTMLInputElement;
const targetLanguageSelect = (await waitForElm(
  "#targetLanguage",
)) as HTMLSelectElement;
const errorMessage = (await waitForElm("#errorMessage")) as HTMLDivElement;
const googleSection = (await waitForElm("#googleSection")) as HTMLDivElement;
const deeplSection = (await waitForElm("#deeplSection")) as HTMLDivElement;
const speedSection = (await waitForElm("#speedSection")) as HTMLDivElement;
const translationSection = (await waitForElm(
  "#translationSection",
)) as HTMLDivElement;
const languageSection = (await waitForElm(
  "#languageSection",
)) as HTMLDivElement;
const speedValue = (await waitForElm("#speedValue")) as HTMLSpanElement;

async function validateApiKey(
  apiKey: string,
  type: "google" | "deepl",
): Promise<boolean> {
  let url = "";
  let body;
  if (type === "google") {
    url = `https://texttospeech.googleapis.com/v1/text:synthesize?key=${apiKey}`;
    body = {
      input: { text: "" },
      voice: { languageCode: "RU" },
      audioConfig: { audioEncoding: "MP3" },
    };
  } else if (type === "deepl") {
    url = `https://api.deepl.com/v2/translate?auth_key=${apiKey}&text=test&target_lang=EN`;
  }
  try {
    const response = await fetch(url, {
      method: "POST",
      body: JSON.stringify(body),
    });
    return response.ok;
  } catch {
    return false;
  }
}

async function saveToStorage(key: string, value: any): Promise<void> {
  await new Promise<void>((resolve) => {
    chrome.storage.local.set({ [key]: value }, () => {
      resolve();
    });
  });
}

function handleGoogleSubmit(): void {
  const apiKey = googleApiKeyInput.value.trim();
  validateApiKey(apiKey, "google")
    .then((result) => {
      if (result) {
        saveToStorage("googleApiKey", apiKey).catch((e) => {
          console.error(e);
        });
        googleSection.classList.add("hidden");
        deeplSection.classList.remove("hidden");
        speedSection.classList.remove("hidden");
      } else {
        errorMessage.classList.remove("hidden");
      }
    })
    .catch((e) => {
      console.error(e);
    });
}

function handleDeeplSubmit(): void {
  const apiKey = deeplApiKeyInput.value.trim();
  validateApiKey(apiKey, "deepl")
    .then((result) => {
      if (result) {
        saveToStorage("deeplApiKey", apiKey).catch((e) => {
          console.error(e);
        });
        deeplSection.classList.add("hidden");
        translationSection.classList.remove("hidden");
        translationSection.classList.add("d-flex");
      } else {
        errorMessage.classList.remove("hidden");
      }
    })
    .catch((e) => {
      console.error(e);
    });
}

function handleTranslationToggle(): void {
  const enabled = translationToggle.checked;
  saveToStorage("translationEnabled", enabled).catch((e) => {
    console.error(e);
  });
  if (enabled) {
    languageSection.classList.remove("hidden");
  } else {
    languageSection.classList.add("hidden");
    targetLanguageSelect.selectedIndex = 0;
    saveToStorage("targetLanguage", targetLanguageSelect.value).catch((e) => {
      console.error(e);
    });
  }
}

function handleSpeedChange(): void {
  const speed = parseFloat(speakingRateInput.value).toFixed(2);
  speedValue.textContent = speed;
  saveToStorage("speakingRate", speed).catch((e) => {
    console.error(e);
  });
}

function handleTargetLanguageChange(): void {
  saveToStorage("targetLanguage", targetLanguageSelect.value).catch((e) => {
    console.error(e);
  });
}

googleSubmitButton.addEventListener("click", handleGoogleSubmit);
deeplSubmitButton.addEventListener("click", handleDeeplSubmit);
translationToggle.addEventListener("change", handleTranslationToggle);
speakingRateInput.addEventListener("input", handleSpeedChange);
targetLanguageSelect.addEventListener("change", handleTargetLanguageChange);

chrome.storage.local.get(
  [
    "googleApiKey",
    "deeplApiKey",
    "targetLanguage",
    "translationEnabled",
    "speakingRate",
  ],
  (data) => {
    if (data.googleApiKey) {
      googleSection.classList.add("hidden");
      deeplSection.classList.remove("hidden");
      speedSection.classList.remove("hidden");
    }
    if (data.deeplApiKey) {
      deeplSection.classList.add("hidden");
      translationSection.classList.remove("hidden");
      translationSection.classList.add("d-flex");
    }
    if (data.translationEnabled) {
      translationToggle.checked = true;
      languageSection.classList.remove("hidden");
    }
    if (data.targetLanguage) {
      targetLanguageSelect.value = data.targetLanguage;
    }
    if (data.speakingRate) {
      speakingRateInput.value = data.speakingRate;
      speedValue.textContent = data.speakingRate;
    }
  },
);
