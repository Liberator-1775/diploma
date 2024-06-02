// eslint-disable-next-line @typescript-eslint/triple-slash-reference
/// <reference types="chrome-types/index.d.ts" />
import { action, waitForElm } from "./utils";

const observeDOM = (function () {
  const MutationObserver = window.MutationObserver;
  return function (obj: Element | null, callback: MutationCallback) {
    if (obj == null || obj.nodeType !== 1) return;
    const mutationObserver = new MutationObserver(callback);
    mutationObserver.observe(obj, { childList: true, subtree: true });
    return mutationObserver;
  };
})();

function createAnchorWithImage(): HTMLAnchorElement {
  const image = new Image(24, 24);
  image.src =
    "data:image/jpeg;base64, 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";

  const link = document.createElement("a");
  link.appendChild(image);
  link.addEventListener("click", handleAnchorClick);
  link.style.cssText = "display: flex";
  return link;
}

function handleAnchorClick(event: Event): void {
  const postContent = (event.target as HTMLElement).parentElement?.parentElement
    ?.parentElement;
  const repliesNextAnchor = postContent?.querySelector(".replies_next");
  if ((repliesNextAnchor as HTMLAnchorElement) !== null) {
    (repliesNextAnchor as HTMLAnchorElement)?.click();
    let messageSent = false;
    const observePostContent = observeDOM(postContent as Element, function (m) {
      m.forEach((record) => {
        record.addedNodes.forEach((node) => {
          const repliesNextAnchor = postContent?.querySelector(".replies_next");
          if ((node as Element).className?.includes("replies_next")) {
            (node as HTMLAnchorElement).click();
          } else if (repliesNextAnchor != null) {
            (repliesNextAnchor as HTMLAnchorElement).click();
          } else if (repliesNextAnchor == null && !messageSent) {
            messageSent = true;
            observePostContent?.disconnect();
            sendVoiceRequest(event.target as HTMLElement, postContent);
          }
        });
      });
    });
  } else {
    sendVoiceRequest(event.target as HTMLElement, postContent);
  }
}

function sendVoiceRequest(
  element: HTMLElement,
  postContent: HTMLElement | null | undefined,
): void {
  let content = "";
  content +=
    "Пост от " +
    element.parentElement?.parentElement?.querySelector(
      "div.PostHeaderInfo.PostHeaderInfo--inHeader > div.PostHeaderTitle > div > h5 > a > span",
    )?.textContent;
  const comments = postContent?.querySelectorAll(".wall_reply_text");
  if (comments != null)
    for (const comment of comments) {
      content +=
        "\n\nКомментарий от " +
        comment.parentElement?.parentElement?.parentElement?.querySelector(
          ".author",
        )?.textContent +
        "\n" +
        comment.textContent;
    }

  chrome.runtime
    .sendMessage({
      action: action.voiceRequest,
      content,
    })
    .catch((e) => {
      console.error("Error sending message: ", e);
    });
}

const element = await waitForElm("#feed_rows");

const postHeaders = document.querySelectorAll(
  ".post > div > div.PostHeader.PostHeader--compact.PostHeader--inPost.js-PostHeader",
);
postHeaders.forEach((header) =>
  header?.insertBefore(createAnchorWithImage(), header.childNodes[4]),
);

observeDOM(element, function (m) {
  m.forEach((record) => {
    record.addedNodes.forEach((node) => {
      if ((node as HTMLDivElement).classList?.contains("feed_row")) {
        const postHeader = (node as HTMLDivElement).querySelector(
          ".post > div > div.PostHeader.PostHeader--compact.PostHeader--inPost.js-PostHeader",
        );
        postHeader?.insertBefore(
          createAnchorWithImage(),
          postHeader.childNodes[4],
        );
      }
    });
  });
});

let audio: HTMLAudioElement | undefined;

chrome.runtime.onMessage.addListener(function (
  message: { action: action; content: string },
  sender,
  sendResponse,
) {
  if (message.action === action.voiceResponse) {
    if (audio !== undefined && !audio.ended) audio.pause();
    audio = new Audio(`data:audio/mp3;base64,${message.content}`);
    audio.play().catch((e) => {
      console.error(e);
    });
  }

  return true;
});

export {};
