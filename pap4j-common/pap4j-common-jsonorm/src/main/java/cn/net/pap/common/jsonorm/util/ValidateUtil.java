package cn.net.pap.common.jsonorm.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ValidateUtil {

    public static final String EMAIL_PATTERN = "^[\\w!#$%&'*+/=?`{|}~^-]+(?:\\.[\\w!#$%&'*+/=?`{|}~^-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,6}$";
    public static final String CHINESE_PATTERN = "^[\u4e00-\u9fa5]{0,}$";
    public static final String CONTAINS_CHINESE_PATTERN = "[\u4E00-\u9FA5|\\！|\\，|\\。|\\（|\\）|\\《|\\》|\\“|\\”|\\？|\\：|\\；|\\【|\\】]";
    public static final String NUMBER_ADN_LETTER = "^[A-Za-z0-9]+$";
    public static final String QQ_PATTERN = "/[1-9][0-9]{4,}/";
    public static final String NUMBER_PATTERN = "[0-9]+";
    public static final String LETTER_PATTERN = "[a-zA-Z]+";
    public static final String ZIPCODE_PATTERN = "\\p{Digit}{6}";
    public static final String PHONE_PATTERN = "^(13[0-9]|14[579]|15[^4,\\D]|16[6]|17[0135678]|18[0-9]|19[89])\\d{8}$";
    public static final String TELEPHONE_PATTERN = "^(0\\d{2,3}-)?(\\d{7,8})(-(\\d{3,}))?$";
    public static final String TELEPHONE_400_PATTERN = "((400)(\\d{7}))|((400)-(\\d{3})-(\\d{4}))";
    public static final String IDCARD_PATTERN = "^((11|12|13|14|15|21|22|23|31"
            + "|32|33|34|35|36|37|41|42|43|44|45|46|50|51|"
            + "52|53|54|61|62|63|64|65|71|81|82|91)\\d{4})"
            + "((((19|20)(([02468][048])|([13579][26]))0229))|"
            + "((20[0-9][0-9])|(19[0-9][0-9]))((((0[1-9])|(1[0-2]))"
            + "((0[1-9])|(1\\d)|(2[0-8])))|((((0[1,3-9])"
            + "|(1[0-2]))(29|30))|(((0[13578])|(1[02]))31))))"
            + "((\\d{3}(x|X))|(\\d{4}))$";
    public static final String USERNAME_PATTERN = "^[A-Za-z0-9_]{3,15}$";
    public static final String PASSWORD_PATTERN = "^(?![0-9]+$)[0-9A-Za-z]{6,20}$";
    public static final String UUID_PATTERN = "[0-9a-z]{8}-[0-9a-z]{4}-[0-9a-z]{4}-[0-9a-z]{4}-[0-9a-z]{12}";
    public static final String YEAR_PATTERN = "^(19|20)\\d{2}$";
    public static final String TIME_PATTERN = "^(?:(?:([01]?\\d|2[0-3]):)?([0-5]?\\d):)?([0-5]?\\d)$";
    public static final String DATE_PATTERN = "^((\\d{2}(([02468][048])|([13579][26]))[\\-\\/\\s]?((((0?[13578])|(1[02]))[\\-\\/\\s]?((0?[1-9])|([1-2][0-9])|(3[01])))|(((0?[469])|(11))[\\-\\/\\s]?((0?[1-9])|([1-2][0-9])|(30)))|(0?2[\\-\\/\\s]?((0?[1-9])|([1-2][0-9])))))|(\\d{2}(([02468][1235679])|([13579][01345789]))[\\-\\/\\s]?((((0?[13578])|(1[02]))[\\-\\/\\s]?((0?[1-9])|([1-2][0-9])|(3[01])))|(((0?[469])|(11))[\\-\\/\\s]?((0?[1-9])|([1-2][0-9])|(30)))|(0?2[\\-\\/\\s]?((0?[1-9])|(1[0-9])|(2[0-8]))))))";
    public static final String TIME_STAMP_PATTERN = "^((\\d{2}(([02468][048])|([13579][26]))[\\-\\s]?((((0?[13578])|(1[02]))[\\-\\s]?((0?[1-9])|([1-2][0-9])|(3[01])))|(((0?[469])|(11))[\\-\\s]?((0?[1-9])|([1-2][0-9])|(30)))|(0?2[\\-\\s]?((0?[1-9])|([1-2][0-9])))))|(\\d{2}(([02468][1235679])|([13579][01345789]))[\\-\\s]?((((0?[13578])|(1[02]))[\\-\\s]?((0?[1-9])|([1-2][0-9])|(3[01])))|(((0?[469])|(11))[\\-\\s]?((0?[1-9])|([1-2][0-9])|(30)))|(0?2[\\-\\s]?((0?[1-9])|(1[0-9])|(2[0-8])))))) ([2][0-3]|[0-1][0-9]|[1-9]):[0-5][0-9]:([0-5][0-9]|[6][0])$";
    public static final String IPV4_PATTERN = "([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])\\.([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])\\.([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])\\.([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])";
    public static final String IPV6_PATTERN = "([0-9a-f]+)\\:([0-9a-f]+)\\:([0-9a-f]+)\\:([0-9a-f]+)\\:([0-9a-f]+)\\:([0-9a-f]+)\\:([0-9a-f]+)\\:([0-9a-f]+)";
    public static final String URL_PATTERN = "^(http|https|ftp)\\://([a-zA-Z0-9\\.\\-]+(\\:[a-zA-Z0-9\\.&%\\$\\-]+)*@)?((25[0-5]|2[0-4][0-9]|[0-1]{1}[0-9]{2}|[1-9]{1}[0-9]{1}|[1-9])\\.(25[0-5]|2[0-4][0-9]|[0-1]{1}[0-9]{2}|[1-9]{1}[0-9]{1}|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1]{1}[0-9]{2}|[1-9]{1}[0-9]{1}|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1]{1}[0-9]{2}|[1-9]{1}[0-9]{1}|[0-9])|([a-zA-Z0-9\\-]+\\.)*[a-zA-Z0-9\\-]+\\.[a-zA-Z]{2,4})(\\:[0-9]+)?(/[^/][a-zA-Z0-9\\.\\,\\?\\'\\\\/\\+&%\\$#\\=~_\\-@]*)*$";
    public static final String DOMAIN_PATTERN = "^(?=^.{3,255}$)[a-zA-Z0-9][-a-zA-Z0-9]{0,62}(\\.[a-zA-Z0-9][-a-zA-Z0-9]{0,62})+$";
    public static final String INT_OR_FLOAT_PATTERN = "^\\d+\\.\\d+|\\d+$";
    public static final String FLOAT_PATTERN = "^(-?\\d+)(\\.\\d+)?$";
    public static final String POSITIVE_INTEGER = "[1-9]+\\d{0,10}";
    public static final String GIT_URL_PATTERN = "(git@[\\w\\.]+)(:(//)?)([\\w\\.@\\:/\\-~]+)(\\.git)(/)?";

    public static boolean validate(String str, String pattern) {
        return isNotEmpty(str) && str.matches(pattern);
    }

    public static boolean isEmail(String email) {
        return validate(email, EMAIL_PATTERN);
    }

    public static boolean isNotEmail(String email) {
        return !isEmail(email);
    }

    public static boolean isChinese(String chineseStr) {
        return validate(chineseStr, CHINESE_PATTERN);
    }

    public static boolean isNotChinese(String chineseStr) {
        return !validate(chineseStr, CHINESE_PATTERN);
    }

    public static boolean isNumberLetter(String str) {
        return validate(str, NUMBER_ADN_LETTER);
    }

    public static boolean isNotNumberLetter(String str) {
        return !validate(str, NUMBER_ADN_LETTER);
    }

    public static boolean isQq(String qq) {
        return validate(qq, QQ_PATTERN);
    }

    public static boolean isNumber(String str) {
        return validate(str, NUMBER_PATTERN);
    }

    public static boolean isNotNumber(String str) {
        return !validate(str, NUMBER_PATTERN);
    }

    public static boolean isLetter(String str) {
        return validate(str, LETTER_PATTERN);
    }

    public static boolean isZipCode(String zipCode) {
        return validate(zipCode, ZIPCODE_PATTERN);
    }

    public static boolean isPhone(String phone) {
        return validate(phone, PHONE_PATTERN);
    }

    public static boolean isNotPhone(String phone) {
        return !isPhone(phone);
    }

    public static boolean isTelephone(String phoneNumber) {
        return validate(phoneNumber, TELEPHONE_PATTERN) || validate(phoneNumber, TELEPHONE_400_PATTERN);
    }

    public static boolean isNotTelephone(String phoneNumber) {
        return !ValidateUtil.isTelephone(phoneNumber);
    }

    public static boolean isIdCard(String cardNumber) {
        return validate(cardNumber, IDCARD_PATTERN);
    }

    public static boolean isNotIdCard(String cardNumber) {
        return !validate(cardNumber, IDCARD_PATTERN);
    }

    public static boolean isUserName(String str) {
        return validate(str, USERNAME_PATTERN);
    }

    public static boolean isPassword(String str) {
        return validate(str, PASSWORD_PATTERN);
    }

    public static boolean isNonNegativeInteger(String str) {
        return validate(str, "^\\d+$");
    }

    public static boolean isUuid(String str) {
        return validate(str, UUID_PATTERN) || validate(str, "[0-9a-z]{32}");
    }

    public static boolean isNotUuid(String str) {
        return !isUuid(str);
    }

    public static boolean isDate(String date) {
        return validate(date, DATE_PATTERN);
    }

    public static boolean isNotDate(String date) {
        return !validate(date, DATE_PATTERN);
    }

    public static boolean isTimestamp(String date) {
        return validate(date, TIME_STAMP_PATTERN);
    }

    public static boolean isNotTimestamp(String date) {
        return !validate(date, TIME_STAMP_PATTERN);
    }

    public static boolean isIP(String ip) {
        if (isEmpty(ip)) {
            return false;
        } else {
            ip = ip.toLowerCase();
            return validate(ip, IPV4_PATTERN) || validate(ip, IPV6_PATTERN);
        }
    }

    public static boolean isNotIP(String ip) {
        return !ValidateUtil.isIP(ip);
    }

    public static boolean isUrl(String url) {
        return validate(url, URL_PATTERN);
    }

    public static boolean isNotUrl(String url) {
        return !validate(url, URL_PATTERN);
    }

    public static boolean isDomain(String domain) {
        return validate(domain, DOMAIN_PATTERN);
    }

    public static boolean isNotDomain(String domain) {
        return !validate(domain, DOMAIN_PATTERN);
    }

    public static boolean isIntOrFloat(String number) {
        return validate(number, INT_OR_FLOAT_PATTERN);
    }

    public static boolean isNotIntOrFloat(String number) {
        return !validate(number, INT_OR_FLOAT_PATTERN);
    }

    public static boolean isFloat(String number) {
        return validate(number, FLOAT_PATTERN);
    }

    public static boolean isNotFloat(String number) {
        return !validate(number, FLOAT_PATTERN);
    }

    public static boolean isNegativeFloat(String number) {
        return validate(number, "^(-((\\d+\\.\\d*[1-9]\\d*)|(\\d*[1-9]\\d*\\.\\d+)|(\\d*[1-9]\\d*)))$");
    }

    public static boolean isNotNegativeFloat(String number) {
        return !isNegativeFloat(number);
    }

    public static boolean isPositiveFloat(String number) {
        return validate(number, "^((\\d+\\.\\d*[1-9]\\d*)|(\\d*[1-9]\\d*\\.\\d+)|(\\d*[1-9]\\d*))$");
    }

    public static boolean isNotPositiveFloat(String number) {
        return !isPositiveFloat(number);
    }

    public static boolean isPositiveInteger(String number) {
        return validate(number, POSITIVE_INTEGER);
    }

    public static boolean isYear(String yearNumber) {
        return validate(yearNumber, YEAR_PATTERN);
    }

    public static boolean isNotYear(String yearNumber) {
        return !isYear(yearNumber);
    }

    public static boolean isTime(String time) {
        return validate(time, TIME_PATTERN);
    }

    public static boolean isNotTime(String time) {
        return !isTime(time);
    }

    public static boolean isContainsChinese(String str) {
        if (isEmpty(str)) {
            return true;
        }
        Pattern p = Pattern.compile(CONTAINS_CHINESE_PATTERN);
        Matcher m = p.matcher(str);
        return m.find();
    }

    public static boolean isGitUrl(String gitUrl) {
        return validate(gitUrl, GIT_URL_PATTERN);
    }

    public static boolean isEmpty(String str) {
        return null == str || "".equals(str.trim()) || "null".equals(str.trim()) || "NaN".equals(str.trim());
    }

    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }
}
