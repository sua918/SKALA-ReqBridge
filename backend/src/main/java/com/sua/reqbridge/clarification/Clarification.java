package com.sua.reqbridge.clarification;

import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.sua.reqbridge.contract.ClarificationSource;
import com.sua.reqbridge.contract.ClarificationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "clarification", schema = "app")
public class Clarification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "requirement_id", nullable = false)
	private Long requirementId;

	@Column(name = "ambiguity_issue_id", nullable = false)
	private Long issueId;

	@Column(name = "round_no", nullable = false)
	private int roundNo;

	@Column(name = "question_text", nullable = false)
	private String questionText;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "question_source", nullable = false, columnDefinition = "clarification_source")
	private ClarificationSource questionSource;

	@Column(name = "answer_text")
	private String answerText;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false, columnDefinition = "clarification_status")
	private ClarificationStatus status;

	protected Clarification() {
	}

	private Clarification(long requirementId, long issueId, int roundNo, String questionText) {
		this(requirementId, issueId, roundNo, questionText, ClarificationSource.AI);
	}

	private Clarification(long requirementId, long issueId, int roundNo, String questionText,
			ClarificationSource questionSource) {
		if (roundNo < 1) {
			throw new IllegalArgumentException("roundNo는 1 이상이어야 합니다.");
		}
		this.requirementId = requirementId;
		this.issueId = issueId;
		this.roundNo = roundNo;
		this.questionText = Objects.requireNonNull(questionText);
		this.questionSource = Objects.requireNonNull(questionSource);
		this.status = ClarificationStatus.WAITING;
	}

	public static Clarification waiting(
			long requirementId, long issueId, int roundNo, String questionText) {
		return new Clarification(requirementId, issueId, roundNo, questionText, ClarificationSource.AI);
	}

	public static Clarification waiting(
			long requirementId, long issueId, int roundNo, String questionText,
			ClarificationSource questionSource) {
		return new Clarification(requirementId, issueId, roundNo, questionText, questionSource);
	}

	public void answer(String answerText) {
		if (status != ClarificationStatus.WAITING) {
			throw new IllegalStateException("WAITING 질문에만 답변할 수 있습니다.");
		}
		this.answerText = Objects.requireNonNull(answerText);
		this.status = ClarificationStatus.ANSWERED;
	}

	public void resolve() {
		if (status != ClarificationStatus.ANSWERED) {
			throw new IllegalStateException("ANSWERED 질문만 해결할 수 있습니다.");
		}
		status = ClarificationStatus.RESOLVED;
	}

	public Long getId() {
		return id;
	}

	public Long getRequirementId() {
		return requirementId;
	}

	public Long getIssueId() {
		return issueId;
	}

	public int getRoundNo() {
		return roundNo;
	}

	public String getQuestionText() {
		return questionText;
	}

	public ClarificationSource getQuestionSource() {
		return questionSource;
	}

	public String getAnswerText() {
		return answerText;
	}

	public ClarificationStatus getStatus() {
		return status;
	}
}
