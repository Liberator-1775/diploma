// eslint-disable-next-line @typescript-eslint/triple-slash-reference
/// <reference types="chrome-types/index.d.ts" />

// eslint-disable-next-line @typescript-eslint/no-misused-promises
const a = setInterval(async function (): Promise<void> {
  if (document.body.getElementsByClassName("replies_list").length !== 0) {
    clearInterval(a);
    // раскрытие всех комментов
    await start();
  }
}, 10000);

async function start(): Promise<void> {
  const expandCommentsAnchors =
    document.body.getElementsByClassName("replies_next");
  for (const expandCommentsAnchor of expandCommentsAnchors) {
    (expandCommentsAnchor as HTMLAnchorElement).click();
  }
  setTimeout(function () {}, 2000);

  let content = "";

  // извлечение источника постов и их комментариев с авторами
  const posts = document.body.getElementsByClassName("post_content");
  for (const post of posts) {
    console.log(
      post.parentElement?.getElementsByClassName(
        "PostHeaderTitle__authorName",
      )[0],
    );
    content +=
      "Пост от " +
      post.parentElement?.getElementsByClassName(
        "PostHeaderTitle__authorName",
      )[0].textContent +
      "\n\n";
    const comments = post.getElementsByClassName("wall_reply_text");
    for (const comment of comments) {
      content +=
        "Комментарий от " +
        comment.parentElement?.parentElement?.parentElement?.getElementsByClassName(
          "author",
        )[0].textContent +
        "\n" +
        comment.textContent +
        "\n\n";
    }
  }

  // отправка в background.ts (следует перепроверить так ли это делается по документации
  await chrome.runtime.sendMessage({ content });
}

chrome.runtime.onMessage.addListener(function (request, sender, sendResponse):
  | boolean
  | undefined {
  /* new Audio(request); */
  return true;
});

export {};
