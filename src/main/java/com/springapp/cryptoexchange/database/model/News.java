package com.springapp.cryptoexchange.database.model;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.util.Date;

@Table(name = "news")
@Entity
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@RequiredArgsConstructor
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class News {
    @Id
    @GeneratedValue
    long id;

    @Column(name = "time", updatable = false)
    Date time = new Date();

    @Column(name = "title")
    @NonNull String title;

    @Column(name = "text")
    @Lob
    @NonNull String text;
}
