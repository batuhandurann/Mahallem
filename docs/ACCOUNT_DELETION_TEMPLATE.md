# Account deletion web-resource template

> Publish this content at a stable public HTTPS URL before Google Play production/closed/open testing that requires the deletion URL. Replace every required field.

# Delete your [REQUIRED: FINAL APP NAME] account

Developer: [REQUIRED: LEGAL ENTITY / DEVELOPER NAME]  
Privacy contact: [REQUIRED: CONTACT]

## Delete from the Android app

1. Sign in to the account.
2. Open **Hesap** → **Hesap ve Gizlilik**.
3. Tap **Hesabımı Sil**.
4. Confirm the deletion request.

The current backend places the account into a 30-day deletion-request period. Normal marketplace activity is blocked during this state. Before purge begins, signing back in exposes **Silme Talebini İptal Et**.

## Request deletion outside the app

[REQUIRED: PROVIDE A REAL REQUEST FORM OR CONTACT MECHANISM THAT CAN AUTHENTICATE/VERIFY THE REQUESTER WITHOUT CREATING UNDUE FRICTION.]

Required request information should be limited to what is necessary to locate and verify the account.

## What is deleted

The purge removes the Firebase Auth account, user profile/subcollections, registered devices, user-scoped uploads and directly identifying account references. Retained marketplace/payment/dispute records are anonymized where the service needs to preserve transaction/security history.

## What may be retained

[REQUIRED: DESCRIBE ANY LEGAL, PAYMENT, FRAUD/SECURITY OR DISPUTE RETENTION; INCLUDE RETENTION PERIODS/CRITERIA.]

Privacy policy: [REQUIRED: PUBLIC HTTPS PRIVACY POLICY URL]
