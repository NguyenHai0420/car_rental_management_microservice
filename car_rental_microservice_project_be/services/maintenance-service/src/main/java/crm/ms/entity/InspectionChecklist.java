package crm.ms.entity;

import crm.ms.enums.InspectionResult;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Checklist kiểm định kỹ thuật cho mỗi lần bảo dưỡng.
 * Mỗi check_item là 1 hạng mục kiểm tra (e.g. "Phanh trước",
 * "Áp suất lốp", "Đèn chiếu sáng", ...).
 */
@Entity
@Table(name = "inspection_checklists", indexes = {
        @Index(name = "idx_ichecklist_schedule", columnList = "schedule_id"),
        @Index(name = "idx_ichecklist_result", columnList = "result")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InspectionChecklist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ichecklist_schedule"))
    private MaintenanceSchedule schedule;

    /** Hạng mục kiểm tra */
    @Column(name = "check_item", nullable = false, length = 200)
    private String checkItem;

    /** Nhóm: Brakes, Tires, Lights, Engine, ... */
    @Column(name = "check_category", length = 100)
    private String checkCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private InspectionResult result = InspectionResult.NOT_CHECKED;

    /** Giá trị đo (e.g. "3.2mm", "32 PSI") */
    @Column(name = "measured_value", length = 100)
    private String measuredValue;

    /** Phạm vi chấp nhận (e.g. "3.0-5.0mm") */
    @Column(name = "acceptable_range", length = 100)
    private String acceptableRange;

    @Column(name = "inspector_notes", columnDefinition = "TEXT")
    private String inspectorNotes;

    @Column(name = "inspector_name", length = 100)
    private String inspectorName;

    @Column(name = "inspected_at")
    private LocalDateTime inspectedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
