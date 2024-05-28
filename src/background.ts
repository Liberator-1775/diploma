// eslint-disable-next-line @typescript-eslint/triple-slash-reference
/// <reference types="chrome-types/index.d.ts" />

chrome.runtime.onMessage.addListener(function (message: string) {
  console.log(message);
  return true;
});
