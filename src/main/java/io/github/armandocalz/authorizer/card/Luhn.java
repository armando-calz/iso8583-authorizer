package io.github.armandocalz.authorizer.card;

/** The Luhn (mod 10) check digit used by card numbers (ISO/IEC 7812). */
public final class Luhn {

    private Luhn() {
    }

    public static boolean isValid(String digits) {
        if (digits == null || digits.length() < 2 || !digits.chars().allMatch(Character::isDigit)) {
            return false;
        }
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';
            if (doubleIt) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }
}
