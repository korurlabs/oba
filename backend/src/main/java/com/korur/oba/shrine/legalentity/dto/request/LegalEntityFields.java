package com.korur.oba.shrine.legalentity.dto.request;

/**
 * The fields of a legal entity that a create and an update both carry. A create and a full
 * replace write the same columns the same way, so
 * {@link com.korur.oba.shrine.legalentity.service.impl.LegalEntityServiceImpl} stores either
 * request through one method body; the records implement it for free.
 */
public interface LegalEntityFields {

    String shortName();

    String legalName();

    String country();

    String registrationNumber();

    String vatNumber();

    String street();

    String postalCode();

    String city();

    String governingLaw();

    String court();
}
