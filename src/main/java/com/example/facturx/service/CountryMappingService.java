package com.example.facturx.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for mapping country names (German and English) to ISO-3166-1 alpha-2 country codes.
 * 
 * The service receives free-text country names like "Germany", "Deutschland", "Switzerland", "Österreich" etc.
 * Task: Convert this name to the official ISO-3166-1 alpha-2 country code (e.g. "DE", "CH", "AT").
 * 
 * Features:
 * - Compares both English and German country names
 * - Ignores case sensitivity and special characters
 * - Returns "DE" as default if no country is uniquely recognized
 */
@Service
public class CountryMappingService {

    private static final String DEFAULT_COUNTRY_CODE = "DE";
    
    private final Map<String, String> countryMapping;

    public CountryMappingService() {
        this.countryMapping = initializeCountryMapping();
    }

    /**
     * Maps a country name to its ISO-3166-1 alpha-2 code.
     * 
     * @param countryName The country name in German or English (case-insensitive)
     * @return The ISO-3166-1 alpha-2 country code, or "DE" as default
     */
    public String getCountryCode(String countryName) {
        if (countryName == null || countryName.trim().isEmpty()) {
            return DEFAULT_COUNTRY_CODE;
        }

        // Normalize the input: remove special characters, convert to lowercase
        String normalized = normalizeCountryName(countryName);
        
        // Direct lookup
        String code = countryMapping.get(normalized);
        if (code != null) {
            return code;
        }

        // If no exact match found, return default
        return DEFAULT_COUNTRY_CODE;
    }

    /**
     * Normalizes a country name for lookup by removing special characters and converting to lowercase.
     */
    private String normalizeCountryName(String countryName) {
        return countryName
            .toLowerCase()
            .replaceAll("[^a-zäöüß]", "") // Remove all non-letter characters except German umlauts
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss");
    }

    /**
     * Initializes the country mapping with German and English country names.
     */
    private Map<String, String> initializeCountryMapping() {
        Map<String, String> mapping = new HashMap<>();
        
        // Afghanistan
        mapping.put("afghanistan", "AF");
        
        // Albania
        mapping.put("albanien", "AL");
        mapping.put("albania", "AL");
        
        // Algeria
        mapping.put("algerien", "DZ");
        mapping.put("algeria", "DZ");
        
        // Andorra
        mapping.put("andorra", "AD");
        
        // Angola
        mapping.put("angola", "AO");
        
        // Antigua and Barbuda
        mapping.put("antiguaundbarbuda", "AG");
        mapping.put("antiguaandbarbuda", "AG");
        
        // Argentina
        mapping.put("argentinien", "AR");
        mapping.put("argentina", "AR");
        
        // Armenia
        mapping.put("armenien", "AM");
        mapping.put("armenia", "AM");
        
        // Australia
        mapping.put("australien", "AU");
        mapping.put("australia", "AU");
        
        // Austria
        mapping.put("oesterreich", "AT");
        mapping.put("austria", "AT");
        
        // Azerbaijan
        mapping.put("aserbaidschan", "AZ");
        mapping.put("azerbaijan", "AZ");
        
        // Bahamas
        mapping.put("bahamas", "BS");
        
        // Bahrain
        mapping.put("bahrain", "BH");
        
        // Bangladesh
        mapping.put("bangladesch", "BD");
        mapping.put("bangladesh", "BD");
        
        // Barbados
        mapping.put("barbados", "BB");
        
        // Belarus
        mapping.put("belarus", "BY");
        mapping.put("weissrussland", "BY");
        
        // Belgium
        mapping.put("belgien", "BE");
        mapping.put("belgium", "BE");
        
        // Belize
        mapping.put("belize", "BZ");
        
        // Benin
        mapping.put("benin", "BJ");
        
        // Bhutan
        mapping.put("bhutan", "BT");
        
        // Bolivia
        mapping.put("bolivien", "BO");
        mapping.put("bolivia", "BO");
        
        // Bosnia and Herzegovina
        mapping.put("bosnienundherzegowina", "BA");
        mapping.put("bosniaandherzegovina", "BA");
        
        // Botswana
        mapping.put("botswana", "BW");
        
        // Brazil
        mapping.put("brasilien", "BR");
        mapping.put("brazil", "BR");
        
        // Brunei
        mapping.put("brunei", "BN");
        mapping.put("bruneidarussalam", "BN");
        
        // Bulgaria
        mapping.put("bulgarien", "BG");
        mapping.put("bulgaria", "BG");
        
        // Burkina Faso
        mapping.put("burkinafaso", "BF");
        
        // Burundi
        mapping.put("burundi", "BI");
        
        // Cambodia
        mapping.put("kambodscha", "KH");
        mapping.put("cambodia", "KH");
        
        // Cameroon
        mapping.put("kamerun", "CM");
        mapping.put("cameroon", "CM");
        
        // Canada
        mapping.put("kanada", "CA");
        mapping.put("canada", "CA");
        
        // Cape Verde
        mapping.put("kapverde", "CV");
        mapping.put("capeverde", "CV");
        
        // Central African Republic
        mapping.put("zentralafrikanischerep", "CF");
        mapping.put("centralafricanrep", "CF");
        
        // Chad
        mapping.put("tschad", "TD");
        mapping.put("chad", "TD");
        
        // Chile
        mapping.put("chile", "CL");
        
        // China
        mapping.put("china", "CN");
        
        // Colombia
        mapping.put("kolumbien", "CO");
        mapping.put("colombia", "CO");
        
        // Comoros
        mapping.put("komoren", "KM");
        mapping.put("comoros", "KM");
        
        // Congo
        mapping.put("kongo", "CG");
        mapping.put("congo", "CG");
        
        // Democratic Republic of the Congo
        mapping.put("kongokinshasa", "CD");
        mapping.put("congodr", "CD");
        
        // Costa Rica
        mapping.put("costarica", "CR");
        
        // Croatia
        mapping.put("kroatien", "HR");
        mapping.put("croatia", "HR");
        
        // Cuba
        mapping.put("kuba", "CU");
        mapping.put("cuba", "CU");
        
        // Cyprus
        mapping.put("zypern", "CY");
        mapping.put("cyprus", "CY");
        
        // Czech Republic
        mapping.put("tschechien", "CZ");
        mapping.put("czechia", "CZ");
        mapping.put("czechrepublic", "CZ");
        
        // Denmark
        mapping.put("daenemark", "DK");
        mapping.put("denmark", "DK");
        
        // Djibouti
        mapping.put("djibouti", "DJ");
        
        // Dominica
        mapping.put("dominica", "DM");
        
        // Dominican Republic
        mapping.put("dominikanischerepublik", "DO");
        mapping.put("dominicanrepublic", "DO");
        
        // Ecuador
        mapping.put("ecuador", "EC");
        
        // Egypt
        mapping.put("aegypten", "EG");
        mapping.put("egypt", "EG");
        
        // El Salvador
        mapping.put("elsalvador", "SV");
        
        // Equatorial Guinea
        mapping.put("aequatorialguinea", "GQ");
        mapping.put("equatorialguinea", "GQ");
        
        // Eritrea
        mapping.put("eritrea", "ER");
        
        // Estonia
        mapping.put("estland", "EE");
        mapping.put("estonia", "EE");
        
        // Eswatini
        mapping.put("eswatini", "SZ");
        mapping.put("swaziland", "SZ");
        
        // Ethiopia
        mapping.put("aethiopien", "ET");
        mapping.put("ethiopia", "ET");
        
        // Fiji
        mapping.put("fidschi", "FJ");
        mapping.put("fiji", "FJ");
        
        // Finland
        mapping.put("finnland", "FI");
        mapping.put("finland", "FI");
        
        // France
        mapping.put("frankreich", "FR");
        mapping.put("france", "FR");
        
        // Gabon
        mapping.put("gabun", "GA");
        mapping.put("gabon", "GA");
        
        // Gambia
        mapping.put("gambia", "GM");
        
        // Georgia
        mapping.put("georgien", "GE");
        mapping.put("georgia", "GE");
        
        // Germany
        mapping.put("deutschland", "DE");
        mapping.put("germany", "DE");
        
        // Ghana
        mapping.put("ghana", "GH");
        
        // Greece
        mapping.put("griechenland", "GR");
        mapping.put("greece", "GR");
        
        // Grenada
        mapping.put("grenada", "GD");
        
        // Guatemala
        mapping.put("guatemala", "GT");
        
        // Guinea
        mapping.put("guinea", "GN");
        
        // Guinea-Bissau
        mapping.put("guineabissau", "GW");
        
        // Guyana
        mapping.put("guyana", "GY");
        
        // Haiti
        mapping.put("haiti", "HT");
        
        // Honduras
        mapping.put("honduras", "HN");
        
        // Hungary
        mapping.put("ungarn", "HU");
        mapping.put("hungary", "HU");
        
        // Iceland
        mapping.put("island", "IS");
        mapping.put("iceland", "IS");
        
        // India
        mapping.put("indien", "IN");
        mapping.put("india", "IN");
        
        // Indonesia
        mapping.put("indonesien", "ID");
        mapping.put("indonesia", "ID");
        
        // Iran
        mapping.put("iran", "IR");
        
        // Iraq
        mapping.put("irak", "IQ");
        mapping.put("iraq", "IQ");
        
        // Ireland
        mapping.put("irland", "IE");
        mapping.put("ireland", "IE");
        
        // Israel
        mapping.put("israel", "IL");
        
        // Italy
        mapping.put("italien", "IT");
        mapping.put("italy", "IT");
        
        // Jamaica
        mapping.put("jamaika", "JM");
        mapping.put("jamaica", "JM");
        
        // Japan
        mapping.put("japan", "JP");
        
        // Jordan
        mapping.put("jordanien", "JO");
        mapping.put("jordan", "JO");
        
        // Kazakhstan
        mapping.put("kasachstan", "KZ");
        mapping.put("kazakhstan", "KZ");
        
        // Kenya
        mapping.put("kenia", "KE");
        mapping.put("kenya", "KE");
        
        // Kiribati
        mapping.put("kiribati", "KI");
        
        // North Korea
        mapping.put("koreanord", "KP");
        mapping.put("northkorea", "KP");
        
        // South Korea
        mapping.put("koreasued", "KR");
        mapping.put("southkorea", "KR");
        
        // Kuwait
        mapping.put("kuwait", "KW");
        
        // Kyrgyzstan
        mapping.put("kirgisistan", "KG");
        mapping.put("kyrgyzstan", "KG");
        
        // Laos
        mapping.put("laos", "LA");
        
        // Latvia
        mapping.put("lettland", "LV");
        mapping.put("latvia", "LV");
        
        // Lebanon
        mapping.put("libanon", "LB");
        mapping.put("lebanon", "LB");
        
        // Lesotho
        mapping.put("lesotho", "LS");
        
        // Liberia
        mapping.put("liberia", "LR");
        
        // Libya
        mapping.put("libyen", "LY");
        mapping.put("libya", "LY");
        
        // Liechtenstein
        mapping.put("liechtenstein", "LI");
        
        // Lithuania
        mapping.put("litauen", "LT");
        mapping.put("lithuania", "LT");
        
        // Luxembourg
        mapping.put("luxemburg", "LU");
        mapping.put("luxembourg", "LU");
        
        // Madagascar
        mapping.put("madagaskar", "MG");
        mapping.put("madagascar", "MG");
        
        // Malawi
        mapping.put("malawi", "MW");
        
        // Malaysia
        mapping.put("malaysia", "MY");
        
        // Maldives
        mapping.put("malediven", "MV");
        mapping.put("maldives", "MV");
        
        // Mali
        mapping.put("mali", "ML");
        
        // Malta
        mapping.put("malta", "MT");
        
        // Marshall Islands
        mapping.put("marshallinseln", "MH");
        mapping.put("marshallislands", "MH");
        
        // Mauritania
        mapping.put("mauretanien", "MR");
        mapping.put("mauritania", "MR");
        
        // Mauritius
        mapping.put("mauritius", "MU");
        
        // Mexico
        mapping.put("mexiko", "MX");
        mapping.put("mexico", "MX");
        
        // Micronesia
        mapping.put("mikronesien", "FM");
        mapping.put("micronesia", "FM");
        
        // Moldova
        mapping.put("moldau", "MD");
        mapping.put("moldova", "MD");
        
        // Monaco
        mapping.put("monaco", "MC");
        
        // Mongolia
        mapping.put("mongolei", "MN");
        mapping.put("mongolia", "MN");
        
        // Montenegro
        mapping.put("montenegro", "ME");
        
        // Morocco
        mapping.put("marokko", "MA");
        mapping.put("morocco", "MA");
        
        // Mozambique
        mapping.put("mosambik", "MZ");
        mapping.put("mozambique", "MZ");
        
        // Myanmar
        mapping.put("myanmar", "MM");
        mapping.put("burma", "MM");
        
        // Namibia
        mapping.put("namibia", "NA");
        
        // Nauru
        mapping.put("nauru", "NR");
        
        // Nepal
        mapping.put("nepal", "NP");
        
        // Netherlands
        mapping.put("niederlande", "NL");
        mapping.put("netherlands", "NL");
        mapping.put("holland", "NL");
        
        // New Zealand
        mapping.put("neuseeland", "NZ");
        mapping.put("newzealand", "NZ");
        
        // Nicaragua
        mapping.put("nicaragua", "NI");
        
        // Niger
        mapping.put("niger", "NE");
        
        // Nigeria
        mapping.put("nigeria", "NG");
        
        // North Macedonia
        mapping.put("nordmazedonien", "MK");
        mapping.put("northmacedonia", "MK");
        mapping.put("mazedonien", "MK");
        mapping.put("macedonia", "MK");
        
        // Norway
        mapping.put("norwegen", "NO");
        mapping.put("norway", "NO");
        
        // Oman
        mapping.put("oman", "OM");
        
        // Pakistan
        mapping.put("pakistan", "PK");
        
        // Palestine
        mapping.put("palaestina", "PS");
        mapping.put("palestine", "PS");
        
        // Panama
        mapping.put("panama", "PA");
        
        // Papua New Guinea
        mapping.put("papuaneuguinea", "PG");
        mapping.put("papuanewguinea", "PG");
        
        // Paraguay
        mapping.put("paraguay", "PY");
        
        // Peru
        mapping.put("peru", "PE");
        
        // Philippines
        mapping.put("philippinen", "PH");
        mapping.put("philippines", "PH");
        
        // Poland
        mapping.put("polen", "PL");
        mapping.put("poland", "PL");
        
        // Portugal
        mapping.put("portugal", "PT");
        
        // Qatar
        mapping.put("katar", "QA");
        mapping.put("qatar", "QA");
        
        // Romania
        mapping.put("rumaenien", "RO");
        mapping.put("romania", "RO");
        
        // Russia
        mapping.put("russland", "RU");
        mapping.put("russia", "RU");
        
        // Rwanda
        mapping.put("ruanda", "RW");
        mapping.put("rwanda", "RW");
        
        // Saint Kitts and Nevis
        mapping.put("stkittsundnevis", "KN");
        mapping.put("saintkittsandnevis", "KN");
        
        // Saint Lucia
        mapping.put("stlucia", "LC");
        mapping.put("saintlucia", "LC");
        
        // Saint Vincent and the Grenadines
        mapping.put("stvincentgrenadinen", "VC");
        mapping.put("saintvincentgrenadines", "VC");
        
        // Samoa
        mapping.put("samoa", "WS");
        
        // San Marino
        mapping.put("sanmarino", "SM");
        
        // São Tomé and Príncipe
        mapping.put("saotomeundprincipe", "ST");
        mapping.put("saotomeandprincipe", "ST");
        
        // Saudi Arabia
        mapping.put("saudiarabien", "SA");
        mapping.put("saudiarabia", "SA");
        
        // Senegal
        mapping.put("senegal", "SN");
        
        // Serbia
        mapping.put("serbien", "RS");
        mapping.put("serbia", "RS");
        
        // Seychelles
        mapping.put("seychellen", "SC");
        mapping.put("seychelles", "SC");
        
        // Sierra Leone
        mapping.put("sierraleone", "SL");
        
        // Singapore
        mapping.put("singapur", "SG");
        mapping.put("singapore", "SG");
        
        // Slovakia
        mapping.put("slowakei", "SK");
        mapping.put("slovakia", "SK");
        
        // Slovenia
        mapping.put("slowenien", "SI");
        mapping.put("slovenia", "SI");
        
        // Solomon Islands
        mapping.put("salomonen", "SB");
        mapping.put("solomonislands", "SB");
        
        // Somalia
        mapping.put("somalia", "SO");
        
        // South Africa
        mapping.put("suedafrika", "ZA");
        mapping.put("southafrica", "ZA");
        
        // South Sudan
        mapping.put("suedsudan", "SS");
        mapping.put("southsudan", "SS");
        
        // Spain
        mapping.put("spanien", "ES");
        mapping.put("spain", "ES");
        
        // Sri Lanka
        mapping.put("srilanka", "LK");
        
        // Sudan
        mapping.put("sudan", "SD");
        
        // Suriname
        mapping.put("suriname", "SR");
        
        // Sweden
        mapping.put("schweden", "SE");
        mapping.put("sweden", "SE");
        
        // Switzerland
        mapping.put("schweiz", "CH");
        mapping.put("switzerland", "CH");
        
        // Syria
        mapping.put("syrien", "SY");
        mapping.put("syria", "SY");
        
        // Taiwan
        mapping.put("taiwan", "TW");
        
        // Tajikistan
        mapping.put("tadschikistan", "TJ");
        mapping.put("tajikistan", "TJ");
        
        // Tanzania
        mapping.put("tansania", "TZ");
        mapping.put("tanzania", "TZ");
        
        // Thailand
        mapping.put("thailand", "TH");
        
        // Timor-Leste
        mapping.put("timorleste", "TL");
        mapping.put("easttimor", "TL");
        
        // Togo
        mapping.put("togo", "TG");
        
        // Tonga
        mapping.put("tonga", "TO");
        
        // Trinidad and Tobago
        mapping.put("trinidadundtobago", "TT");
        mapping.put("trinidadandtobago", "TT");
        
        // Tunisia
        mapping.put("Tunesien", "TN");
        mapping.put("tunisia", "TN");
        
        // Turkey
        mapping.put("tuerkei", "TR");
        mapping.put("turkey", "TR");
        mapping.put("tuerkiye", "TR");
        
        // Turkmenistan
        mapping.put("turkmenistan", "TM");
        
        // Tuvalu
        mapping.put("tuvalu", "TV");
        
        // Uganda
        mapping.put("uganda", "UG");
        
        // Ukraine
        mapping.put("ukraine", "UA");
        
        // United Arab Emirates
        mapping.put("vereinigtearabischeemir", "AE");
        mapping.put("unitedarabemirates", "AE");
        
        // United Kingdom
        mapping.put("vereinigteskoenigreich", "GB");
        mapping.put("unitedkingdom", "GB");
        mapping.put("grossbritannien", "GB");
        mapping.put("greatbritain", "GB");
        mapping.put("england", "GB");
        
        // United States
        mapping.put("usa", "US");
        mapping.put("unitedstates", "US");
        mapping.put("amerika", "US");
        mapping.put("america", "US");
        
        // Uruguay
        mapping.put("uruguay", "UY");
        
        // Uzbekistan
        mapping.put("usbekistan", "UZ");
        mapping.put("uzbekistan", "UZ");
        
        // Vanuatu
        mapping.put("vanuatu", "VU");
        
        // Vatican City
        mapping.put("vatikanstadt", "VA");
        mapping.put("vaticancity", "VA");
        
        // Venezuela
        mapping.put("venezuela", "VE");
        
        // Vietnam
        mapping.put("vietnam", "VN");
        
        // Yemen
        mapping.put("jemen", "YE");
        mapping.put("yemen", "YE");
        
        // Zambia
        mapping.put("sambia", "ZM");
        mapping.put("zambia", "ZM");
        
        // Zimbabwe
        mapping.put("simbabwe", "ZW");
        mapping.put("zimbabwe", "ZW");
        
        return mapping;
    }
}
