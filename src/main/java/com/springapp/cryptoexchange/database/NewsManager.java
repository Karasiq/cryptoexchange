package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.News;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NewsManager {
    void addOrModifyNews(News news);
    void removeNews(long id);
    List<News> getNews(int limit);
}
