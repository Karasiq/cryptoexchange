package com.springapp.cryptoexchange.database.model;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import org.apache.http.annotation.Immutable;
import org.hibernate.annotations.*;
import org.hibernate.annotations.Cache;

import javax.persistence.*;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Date;

@Table(name = "news")
@Entity
@Immutable
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
public class News {
    @Id
    @GeneratedValue
    long id;

    @Column(name = "time")
    Date time = new Date();

    @Column(name = "title")
    @NonNull String title;

    @Column(name = "text")
    @NonNull String text;
}
