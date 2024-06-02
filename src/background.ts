import { action } from "./utils";

chrome.runtime.onMessage.addListener(function (
  message: { action: action; content: string },
  sender,
  sendResponse,
) {
  switch (message.action) {
    case action.voiceRequest:
      chrome.storage.local
        .get([
          "googleApiKey",
          "deeplApiKey",
          "targetLanguage",
          "voiceName",
          "translationEnabled",
          "speakingRate",
        ])
        .then((variables) => {
          if (variables.translationEnabled) {
            fetch("https://api.deepl.com/v2/translate", {
              method: "POST",
              headers: {
                Authorization: `DeepL-Auth-Key ${variables.deeplApiKey}`,
                "Content-Type": "application/json",
              },
              body: JSON.stringify({
                text: [message.content],
                target_lang: variables.targetLanguage,
              }),
            })
              .then((translation) => {
                if (!translation.ok)
                  throw new Error(`HTTP error! Status: ${translation.status}`);
                translation
                  .json()
                  .then((translationJson) => {
                    fetch(
                      "https://texttospeech.googleapis.com/v1/text:synthesize?key=" +
                        variables.googleApiKey,
                      {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify({
                          input: { text: translationJson.translations[0].text },
                          voice: {
                            languageCode: variables.targetLanguage,
                            name: variables.voiceName,
                          },
                          audioConfig: { audioEncoding: "MP3" },
                        }),
                      },
                    )
                      .then((voice) => {
                        if (!voice.ok)
                          throw new Error(
                            `HTTP error! Status: ${voice.status}`,
                          );
                        voice
                          .json()
                          .then((voiceJson) => {
                            chrome.tabs.query(
                              { active: true, currentWindow: true },
                              (tabs) => {
                                if (
                                  tabs.length > 0 &&
                                  tabs[0]?.id !== undefined
                                ) {
                                  chrome.tabs
                                    .sendMessage(tabs[0].id, {
                                      action: action.voiceResponse,
                                      content: voiceJson.audioContent,
                                    })
                                    .catch((e) => {
                                      console.error(
                                        "Error sending message: ",
                                        e,
                                      );
                                    });
                                }
                              },
                            );
                          })
                          .catch((e) => {
                            console.error(e);
                          });
                      })
                      .catch((e) => {
                        console.error(e);
                      });
                  })
                  .catch((e) => {
                    console.error(e);
                  });
              })
              .catch((e) => {
                console.error(e);
              });
          } else {
            fetch(
              "https://texttospeech.googleapis.com/v1/text:synthesize?key=" +
                variables.googleApiKey,
              {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                  input: { text: message.content },
                  voice: {
                    languageCode: "ru-RU",
                    name: "ru-RU-Wavenet-A",
                  },
                  audioConfig: { audioEncoding: "MP3" },
                }),
              },
            )
              .then((voice) => {
                if (!voice.ok)
                  throw new Error(`HTTP error! Status: ${voice.status}`);
                voice
                  .json()
                  .then((voiceJson) => {
                    chrome.tabs.query(
                      { active: true, currentWindow: true },
                      (tabs) => {
                        if (tabs.length > 0 && tabs[0]?.id !== undefined) {
                          chrome.tabs
                            .sendMessage(tabs[0].id, {
                              action: action.voiceResponse,
                              content: voiceJson.audioContent,
                            })
                            .catch((e) => {
                              console.error("Error sending message: ", e);
                            });
                        }
                      },
                    );
                  })
                  .catch((e) => {
                    console.error(e);
                  });
              })
              .catch((e) => {
                console.error(e);
              });
          }
        })
        .catch((e) => {
          console.error(e);
        });
      break;
  }
  return true;
});
