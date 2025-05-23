package searchengine.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "`index`")
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class IndexEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @ManyToOne
    @JoinColumn(name = "page_id", nullable = false)
    private PageEntity page;

    @ManyToOne
    @JoinColumn(name = "lemma_id", nullable = false)
    private LemmaEntity lemma;

    @Column(name = "`rank`", nullable = false)
    private Float rank;
}
