package searchengine.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "page", indexes = @Index(name = "idx_page_path", columnList = "path"))
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity site;

    @Column(name = "path", nullable = false, columnDefinition = "VARCHAR(255)")
    private String path;

    @Column(name = "code", nullable = false)
    private int code;

    @ToString.Exclude
    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @ToString.Exclude
    private String title;
}
