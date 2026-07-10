/**
 * Public privacy policy, served at /privacy (no auth). Required for the Google
 * Play listing and India's DPDP Act, because the app stores shop-account details
 * plus end-customers' names, phone numbers, and sales/financial records.
 *
 * Keep this factual and in sync with the Data safety form on Play. If data
 * practices change, update BOTH this page and the Play declaration.
 */
const UPDATED = "10 July 2026";
const CONTACT = "support@plantbill.in";

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="space-y-2">
      <h2 className="text-xl font-bold text-ink">{title}</h2>
      <div className="space-y-2 text-base leading-relaxed text-ink-soft">{children}</div>
    </section>
  );
}

export function PrivacyPolicyPage() {
  return (
    <div className="min-h-dvh bg-surface-muted px-6 py-12">
      <div className="mx-auto w-full max-w-2xl space-y-8">
        <header className="space-y-2">
          <div className="flex items-center gap-3">
            <img src="/logo.png" alt="PlantBill" width={48} height={48} className="rounded-xl" style={{ height: 48, width: 48 }} />
            <h1 className="text-3xl font-extrabold tracking-tight text-ink">PlantBill — Privacy Policy</h1>
          </div>
          <p className="text-sm text-ink-soft">Last updated: {UPDATED}</p>
        </header>

        <p className="text-base leading-relaxed text-ink-soft">
          PlantBill is billing software for plant shops. Each shop is set up by us for a specific business; there is no
          public sign-up. This policy explains what information the app handles, why, and your choices. It applies to the
          PlantBill mobile app and website.
        </p>

        <Section title="Who this data belongs to">
          <p>
            The app is used by shop staff to bill their own customers. Two kinds of people appear in the data:
          </p>
          <ul className="list-disc space-y-1 pl-6">
            <li>
              <strong>Shop users</strong> (owners, managers, salespeople) — the people who log in and operate the app.
            </li>
            <li>
              <strong>Shop customers</strong> — people who buy from the shop. Their details are entered by shop staff at
              the counter, only when the customer provides them.
            </li>
          </ul>
        </Section>

        <Section title="Information we collect">
          <ul className="list-disc space-y-1 pl-6">
            <li>
              <strong>Account details</strong> of shop users: email address and an encrypted (hashed) password.
            </li>
            <li>
              <strong>Customer details</strong> entered on a bill: name and phone number. These are optional and provided
              by the customer to the shop; a phone number is used so the shop can share the customer's own receipt.
            </li>
            <li>
              <strong>Business records</strong>: products, prices, bills, discounts, payment amounts (cash/UPI/due),
              expenses, and daily cash-book entries created by the shop.
            </li>
            <li>
              <strong>Diagnostics</strong>: if the app crashes, a technical crash report (device model, OS version, error
              details) may be sent so we can fix the problem. It does not contain your password.
            </li>
          </ul>
          <p>
            We do <strong>not</strong> collect location, contacts, photos from your device (other than product images a
            shop chooses to upload), advertising identifiers, or biometric data. The app contains no third-party ads.
          </p>
        </Section>

        <Section title="How we use it">
          <ul className="list-disc space-y-1 pl-6">
            <li>To let shop users sign in and operate their shop.</li>
            <li>To create and store bills, sales summaries, and cash-book reports for the shop.</li>
            <li>To let a shop share a receipt with a customer who gave their number.</li>
            <li>To keep the service secure and to diagnose crashes.</li>
          </ul>
          <p>We do not sell personal information, and we do not use it for advertising.</p>
        </Section>

        <Section title="Storage and security">
          <p>
            Data is stored on our own private server. All traffic between the app and the server is encrypted in transit
            (HTTPS/TLS). Passwords are stored only as a one-way hash and are never readable. Each shop's data is isolated
            so one shop cannot see another shop's data.
          </p>
        </Section>

        <Section title="Sharing">
          <p>
            We do not share personal information with third parties for their own use. Information is only processed by
            the infrastructure that runs PlantBill (our server hosting). We may disclose information if required by law.
          </p>
        </Section>

        <Section title="Retention and deletion">
          <p>
            Business and billing records are kept for as long as the shop's account is active, so the shop has its own
            history. A shop owner or user can ask us to delete their account and associated personal data, and a customer
            can ask a shop to remove their contact details. To request deletion, email{" "}
            <a href={`mailto:${CONTACT}`} className="font-semibold text-primary-700 underline">
              {CONTACT}
            </a>
            .
          </p>
        </Section>

        <Section title="Children">
          <p>PlantBill is a business tool intended for adults (18+). It is not directed at children.</p>
        </Section>

        <Section title="Your rights">
          <p>
            Depending on your location (including under India's Digital Personal Data Protection Act), you may have the
            right to access, correct, or delete your personal data, and to withdraw consent. Contact us to exercise these
            rights.
          </p>
        </Section>

        <Section title="Changes">
          <p>
            We may update this policy. Material changes will be reflected here with a new "Last updated" date.
          </p>
        </Section>

        <Section title="Contact">
          <p>
            Questions about this policy or your data? Email{" "}
            <a href={`mailto:${CONTACT}`} className="font-semibold text-primary-700 underline">
              {CONTACT}
            </a>
            .
          </p>
        </Section>

        <footer className="border-t border-border pt-6 text-sm text-ink-soft">
          © {new Date().getFullYear()} PlantBill. All rights reserved.
        </footer>
      </div>
    </div>
  );
}
