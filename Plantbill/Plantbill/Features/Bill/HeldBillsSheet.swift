import SwiftUI

struct HeldBillsSheet: View {
    let viewModel: BillingViewModel
    let onResume: (HeldBill) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var pendingDiscard: HeldBill?

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.heldBills.isEmpty {
                    EmptyStateView(icon: "tray", title: "Held bills", message: "Bills you parked to serve another customer. Tap Resume to continue one.")
                } else {
                    List {
                        ForEach(viewModel.heldBills) { held in
                            HeldBillRow(held: held) {
                                onResume(held)
                            } onDiscard: {
                                pendingDiscard = held
                            }
                            .listRowSeparator(.hidden)
                            .listRowBackground(Color.clear)
                            .listRowInsets(EdgeInsets(top: PlantbillSpacing.xs, leading: PlantbillSpacing.md, bottom: PlantbillSpacing.xs, trailing: PlantbillSpacing.md))
                        }
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                }
            }
            .background(PlantbillColor.background)
            .navigationTitle("Held bills")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
            .confirmationDialog(
                "Discard this held bill?",
                isPresented: Binding(get: { pendingDiscard != nil }, set: { if !$0 { pendingDiscard = nil } }),
                titleVisibility: .visible
            ) {
                Button("Discard", role: .destructive) {
                    if let pendingDiscard { viewModel.discardHeld(pendingDiscard) }
                    pendingDiscard = nil
                }
                Button("Cancel", role: .cancel) { pendingDiscard = nil }
            }
        }
    }
}

private struct HeldBillRow: View {
    let held: HeldBill
    let onResume: () -> Void
    let onDiscard: () -> Void

    var total: Money {
        held.lines.reduce(Money.zero) { $0 + Money.parse($1.unitPriceWire) * $1.quantity }
    }

    var body: some View {
        PlantbillCard {
            VStack(alignment: .leading, spacing: PlantbillSpacing.sm) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(held.customerName.isEmpty ? "Walk-in" : held.customerName)
                            .font(PlantbillTypography.bodyEmphasized)
                            .foregroundStyle(PlantbillColor.textPrimary)
                        Text("\(held.lines.count) items · \(total.format())")
                            .font(PlantbillTypography.caption)
                            .foregroundStyle(PlantbillColor.textSecondary)
                    }
                    Spacer()
                    Text(held.savedAt, style: .relative)
                        .font(PlantbillTypography.caption)
                        .foregroundStyle(PlantbillColor.textSecondary)
                }

                HStack(spacing: PlantbillSpacing.sm) {
                    SecondaryButton(title: "Discard", tint: PlantbillColor.error, action: onDiscard)
                    PrimaryButton(title: "Resume this bill", action: onResume)
                }
            }
        }
    }
}
