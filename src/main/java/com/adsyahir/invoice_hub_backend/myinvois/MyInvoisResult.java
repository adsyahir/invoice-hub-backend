package com.adsyahir.invoice_hub_backend.myinvois;

/**
 * The outcome of a successful MyInvois submission — the two identifiers LHDN returns for a
 * validated document. The caller turns these into the invoice's stored uuid/longId and the
 * public validation URL.
 *
 * @param uuid   LHDN's document UUID
 * @param longId LHDN's long identifier (used, with the uuid, in the shareable portal URL)
 */
public record MyInvoisResult(String uuid, String longId) {
}
