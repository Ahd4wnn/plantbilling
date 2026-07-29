import SwiftUI

struct BillSuccessView: View {
    let bill: BillOut
    let onNewBill: () -> Void

    var body: some View {
        VStack(spacing: PlantbillSpacing.lg) {
            Spacer()

            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 64))
                .foregroundStyle(PlantbillColor.green)

            Text(bill.idempotentReplay ? "Bill already saved" : "Bill saved")
                .font(PlantbillTypography.title)
                .foregroundStyle(PlantbillColor.textPrimary)

            Text(Money.parse(bill.total).format())
                .font(PlantbillTypography.largeTitle)
                .foregroundStyle(PlantbillColor.green)

            if let customerName = bill.customerName, !customerName.isEmpty {
                Text(customerName)
                    .font(PlantbillTypography.body)
                    .foregroundStyle(PlantbillColor.textSecondary)
            }

            Spacer()

            VStack(spacing: PlantbillSpacing.sm) {
                SecondaryButton(title: "Print receipt", isDisabled: true) {}
                Text("Printing is coming in a future update.")
                    .font(PlantbillTypography.caption)
                    .foregroundStyle(PlantbillColor.textSecondary)

                PrimaryButton(title: "New bill", action: onNewBill)
                    .padding(.top, PlantbillSpacing.sm)
            }
        }
        .padding(PlantbillSpacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(PlantbillColor.background)
    }
}
