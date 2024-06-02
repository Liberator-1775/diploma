export async function waitForElm(selector: string): Promise<Element | null> {
  return await new Promise((resolve) => {
    const existingElement = document.querySelector(selector);
    if (existingElement != null) {
      resolve(existingElement);
      return;
    }
    const observer = new MutationObserver(() => {
      const element = document.querySelector(selector);
      if (element != null) {
        observer.disconnect();
        resolve(element);
      }
    });
    observer.observe(document.body, {
      childList: true,
      subtree: true,
    });
  });
}

export enum action {
  voiceRequest,
  voiceResponse,
}
