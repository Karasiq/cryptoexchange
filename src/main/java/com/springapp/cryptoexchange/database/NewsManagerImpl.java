package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.News;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.apachecommons.CommonsLog;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;

@Repository
@FieldDefaults(level = AccessLevel.PRIVATE)
@CommonsLog
public class NewsManagerImpl implements NewsManager {
    @Autowired
    SessionFactory sessionFactory;

    @Transactional
    @CacheEvict(value = "getNews", allEntries = true)
    public void addOrModifyNews(News news) {
        Session session = sessionFactory.getCurrentSession();
        session.saveOrUpdate(news);
        log.info("News committed: " + news);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public List<News> getNews(int limit) {
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(News.class)
                .addOrder(Order.desc("time"))
                .setMaxResults(limit)
                .list();
    }

    @Transactional
    @CacheEvict(value = "getNews", allEntries = true)
    public void removeNews(long id) {
        Session session = sessionFactory.getCurrentSession();
        Object obj = session.load(News.class, id);
        Assert.notNull(obj, "Specified news not found");
        session.delete(obj);
        log.info("News removed: " + id);
    }
}
