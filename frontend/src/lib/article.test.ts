import { expect, it } from "vitest";
import { safeArticle } from "./article";
it("removes active content, images, forged external links and event handlers", () => {
  const html = safeArticle(
    '<script>bad()</script><p onclick="bad()">Safe <b>text</b></p><iframe></iframe><img src="x" onerror="bad()"><form><input></form><a href="https://evil.test" data-wiki-title="Target">External</a><a href="javascript:bad()">Unsafe</a>',
  );
  expect(html).toContain("<b>text</b>");
  expect(html).toContain("External");
  for (const unsafe of [
    "<script",
    "onclick",
    "<iframe",
    "<img",
    "<form",
    "<input",
    "evil.test",
    "javascript:",
    "data-wiki-title",
  ])
    expect(html).not.toContain(unsafe);
});
it("preserves only controlled movement links and local section anchors", () => {
  const html = safeArticle(
    '<a href="#" data-wiki-title="Graph theory">Graph</a><h2 id="wiki-Intro">Intro</h2><a href="#wiki-Intro">Section</a>',
  );
  expect(html).toContain('data-wiki-title="Graph theory"');
  expect(html).toContain('href="#wiki-Intro"');
});
