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
      "tfoot",
      "colgroup",
      "col",
      "tr",
      "th",
      "td",
      "caption",
      "a",
      "span",
      "div",
    ],
    ALLOWED_ATTR: ["id", "href", "data-wiki-title", "class", "rowspan", "colspan", "scope", "headers", "abbr", "span"],
    ALLOW_DATA_ATTR: false,
    ALLOW_ARIA_ATTR: false,
    RETURN_DOM_FRAGMENT: true,
  });
  const tableClasses = new Set(["infobox", "wikitable", "sortable", "navbox", "navbox-inner", "sidebar", "vertical-navbox"]);
  for (const element of fragment.querySelectorAll("*")) {
    const tag = element.tagName.toLowerCase();
    if (tag === "div" && !element.closest("table")) {
      element.replaceWith(...element.childNodes);
      continue;
    }
    for (const name of ["rowspan", "colspan", "scope", "headers", "abbr", "span"]) {
      const allowed = (tag === "th" && name !== "span") ||
        (tag === "td" && ["rowspan", "colspan", "headers"].includes(name)) ||
        (["col", "colgroup"].includes(tag) && name === "span");
      if (!allowed) element.removeAttribute(name);
    }
    const classes = tag === "table" ? [...element.classList].filter(name => tableClasses.has(name)) : [];
    if (classes.length) element.setAttribute("class", classes.join(" "));
    else element.removeAttribute("class");
  }
  for (const block of [...fragment.querySelectorAll("table div")].reverse()) {
    if (!block.children.length && !block.textContent?.replace(/[\s\u200B\uFEFF]/g, "")) block.remove();
  }
  for (const anchor of fragment.querySelectorAll("a")) {
    const href = anchor.getAttribute("href") ?? "";
    if (href === "#" && anchor.getAttribute("data-wiki-title")?.trim())
      continue;
    anchor.removeAttribute("data-wiki-title");
    if (!href.startsWith("#") || href.length <= 1)
      anchor.replaceWith(...anchor.childNodes);
  }
  // Keep native table formatting; overflow belongs to a separate focusable region.
  for (const table of fragment.querySelectorAll("table")) {
    const wrapper = document.createElement("div");
    wrapper.className = "wiki-table-scroll";
    wrapper.tabIndex = 0;
    wrapper.setAttribute("role", "region");
    wrapper.setAttribute("aria-label", table.caption?.textContent?.trim().slice(0, 160) || "Article table");
    table.before(wrapper);
    wrapper.append(table);
  }
  const container = document.createElement("div");
  container.append(fragment);
  return container.innerHTML;
}
