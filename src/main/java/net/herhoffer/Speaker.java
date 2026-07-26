package net.herhoffer;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

@Entity
public class Speaker extends PanacheEntity {

    @Column(nullable = false)
    public String name;

    public String company;

    @Column(length = 1000)
    public String bio;
}
