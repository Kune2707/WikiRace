import DOMPurify from "dompurify";

export function safeArticle(html: string) {
  const fragment = DOMPurify.sanitize(html, {
    ALLOWED_TAGS: [
      "p",
      "h2",
      "h3",
      "h4",
      "h5",
      "h6",
      "ul",
      "ol",
      "li",
      "dl",
      "dt",
      "dd",
      "strong",
      "em",
      "b",
      "i",
      "sup",
      "sub",
      "br",
      "blockquote",
      "pre",
      "code",
      "table",
      "thead",
      "tbody",
      "tr",
      "th",
      "td",
      "caption",
      "a",
      "span",
    ],
    ALLOWED_ATTR: ["id", "href", "data-wiki-title"],
    ALLOW_DATA_ATTR: false,
    ALLOW_ARIA_ATTR: false,
    RETURN_DOM_FRAGMENT: true,
  });
  for (const anchor of fragment.querySelectorAll("a")) {
    const href = anchor.getAttribute("href") ?? "";
    if (href === "#" && anchor.getAttribute("data-wiki-title")?.trim())
      continue;
    anchor.removeAttribute("data-wiki-title");
    if (!href.startsWith("#") || href.length <= 1)
      anchor.replaceWith(...anchor.childNodes);
  }
  const container = document.createElement("div");
  container.append(fragment);
  return container.innerHTML;
}
