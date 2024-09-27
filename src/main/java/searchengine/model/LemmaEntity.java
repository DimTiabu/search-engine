package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "lemma")
@Data
public class LemmaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity site;

    @Column(name = "lemma", nullable = false)
    private String lemma;

    @Column(name = "frequency", nullable = false)
    private int frequency;
}
