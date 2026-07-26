package net.herhoffer;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

import java.time.LocalDateTime;
import java.util.List;

@Entity
public class Talk extends PanacheEntity {

    @Column(nullable = false)
    public String title;

    @Column(length = 2000)
    public String summary;

    public int durationMinutes;

    public LocalDateTime scheduledAt;

    public String room;

    @ManyToOne(optional = false)
    public Speaker speaker;

    public static List<Talk> bySchedule() {
        return listAll(Sort.by("scheduledAt"));
    }

    public static List<Talk> byRoom(String room) {
        return list("room", Sort.by("scheduledAt"), room);
    }

    public static List<Talk> bySpeaker(Long speakerId) {
        return list("speaker.id", Sort.by("scheduledAt"), speakerId);
    }
}
