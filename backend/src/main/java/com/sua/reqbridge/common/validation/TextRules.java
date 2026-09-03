package com.sua.reqbridge.common.validation;

public final class TextRules {

	private TextRules() {
	}

	public static String requiredTrimmed(String fieldName, String value, int maxCodePoints) {
		if (value == null) {
			throw new IllegalArgumentException(fieldName + " must not be null");
		}
		String normalized = trimContractWhitespace(value);
		validateRequired(fieldName, value, normalized, maxCodePoints);
		return normalized;
	}

	public static String optionalTrimmed(String fieldName, String value, int maxCodePoints) {
		if (value == null) {
			return null;
		}
		String normalized = trimContractWhitespace(value);
		validateRequired(fieldName, value, normalized, maxCodePoints);
		return normalized;
	}

	public static String requiredPreserved(String fieldName, String value, int maxCodePoints) {
		if (value == null) {
			throw new IllegalArgumentException(fieldName + " must not be null");
		}
		validateRequired(fieldName, value, value, maxCodePoints);
		return value;
	}

	private static void validateRequired(
			String fieldName,
			String original,
			String normalized,
			int maxCodePoints) {
		if (normalized.codePoints().allMatch(TextRules::isContractWhitespace)) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
		if (codePointLength(original) > maxCodePoints || codePointLength(normalized) > maxCodePoints) {
			throw new IllegalArgumentException(
					fieldName + " must not exceed " + maxCodePoints + " Unicode code points");
		}
	}

	private static int codePointLength(String value) {
		return value.codePointCount(0, value.length());
	}

	private static String trimContractWhitespace(String value) {
		int start = 0;
		int end = value.length();
		while (start < end) {
			int codePoint = value.codePointAt(start);
			if (!isContractWhitespace(codePoint)) {
				break;
			}
			start += Character.charCount(codePoint);
		}
		while (end > start) {
			int codePoint = value.codePointBefore(end);
			if (!isContractWhitespace(codePoint)) {
				break;
			}
			end -= Character.charCount(codePoint);
		}
		return value.substring(start, end);
	}

	private static boolean isContractWhitespace(int codePoint) {
		return (codePoint >= 0x0009 && codePoint <= 0x000D)
				|| codePoint == 0x0020
				|| codePoint == 0x0085
				|| codePoint == 0x00A0
				|| codePoint == 0x1680
				|| (codePoint >= 0x2000 && codePoint <= 0x200A)
				|| codePoint == 0x2028
				|| codePoint == 0x2029
				|| codePoint == 0x202F
				|| codePoint == 0x205F
				|| codePoint == 0x3000
				|| codePoint == 0xFEFF;
	}
}
