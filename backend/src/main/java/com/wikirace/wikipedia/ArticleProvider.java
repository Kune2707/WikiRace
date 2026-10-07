package com.wikirace.wikipedia;

import java.util.List;

public interface ArticleProvider {
    List<String> search(String query);
    String resolve(String title);
    ArticleData loadArticle(String title);
}
