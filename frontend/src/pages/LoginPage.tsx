import { useState } from "react";
import type { FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "@/store/auth";
import { roleHome } from "@/routes/ProtectedRoute";
import { Wordmark } from "@/components/LeafMark";
import { TextInput, PasswordInput } from "@/components/TextInput";
import { Button } from "@/components/Button";

export function LoginPage() {
  const login = useAuth((s) => s.login);
  const navigate = useNavigate();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const user = await login(email.trim(), password);
      navigate(roleHome(user.role), { replace: true });
    } catch (err) {
      // login() throws a friendly, plain-language message (never a status code).
      setError(err instanceof Error ? err.message : "Email or password is incorrect.");
      setSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-dvh flex-col bg-surface-muted px-6 pt-[env(safe-area-inset-top)]">
      <div className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center py-12">
        <div className="mb-10 flex flex-col items-center text-center">
          <img
            src="/logo.png"
            alt="PlantBill"
            width={88}
            height={88}
            className="mb-4 rounded-2xl shadow-sm"
            style={{ height: 88, width: 88 }}
          />
          <Wordmark className="scale-110" />
          <p className="mt-3 text-lg text-ink-soft">Simple billing for your plant shop.</p>
        </div>

        <form onSubmit={onSubmit} noValidate className="space-y-5">
          <TextInput
            label="Email"
            type="email"
            inputMode="email"
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            placeholder="you@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />

          <PasswordInput
            label="Password"
            autoComplete="current-password"
            placeholder="Your password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />

          {error && (
            <div
              role="alert"
              className="rounded-control border-2 border-danger/40 bg-danger-soft px-4 py-3
                         text-base font-semibold text-danger"
            >
              {error}
            </div>
          )}

          <Button
            type="submit"
            variant="primary"
            size="action"
            loading={submitting}
            loadingLabel="Logging in…"
            disabled={!email || !password}
          >
            Log In
          </Button>
        </form>

        <p className="mt-8 text-center text-base text-ink-soft">
          Accounts are created by PlantBill. Ask your provider if you can't sign in.
        </p>
      </div>
    </div>
  );
}
