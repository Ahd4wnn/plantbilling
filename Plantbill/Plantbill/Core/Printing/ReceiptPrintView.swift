import SwiftUI

/// Plain black-on-white receipt layout for printing — deliberately not using
/// the app's design-system colors/fonts (those are for on-screen legibility;
/// a printed receipt should look like a receipt). Sized for thermal roll
/// paper; the printer/driver scales it to the physical paper width.
struct ReceiptPrintView: View {
    let data: ReceiptPrintData

    private let width: CGFloat = 300

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            VStack(spacing: 2) {
                Text(data.businessName)
                    .font(.system(size: 16, weight: .bold))
                if let address = data.businessAddress, !address.isEmpty {
                    Text(address).font(.system(size: 10))
                }
                if let phone = data.businessPhone, !phone.isEmpty {
                    Text(phone).font(.system(size: 10))
                }
            }
            .frame(maxWidth: .infinity, alignment: .center)
            .multilineTextAlignment(.center)

            Divider()

            Text(data.createdAt.formatted(date: .abbreviated, time: .shortened))
                .font(.system(size: 10))
            if let name = data.customerName, !name.isEmpty {
                Text(data.customerPhone.map { "\(name) • \($0)" } ?? name)
                    .font(.system(size: 10))
            }
            if data.isEdited {
                Text("Edited").font(.system(size: 10, weight: .bold))
            }

            Divider()

            ForEach(Array(data.items.enumerated()), id: \.offset) { _, item in
                VStack(alignment: .leading, spacing: 1) {
                    Text(item.name).font(.system(size: 11, weight: .semibold))
                    HStack {
                        Text("\(item.quantity) × \(item.unitPrice.format())")
                            .font(.system(size: 10))
                        Spacer()
                        Text(item.lineTotal.format())
                            .font(.system(size: 11))
                    }
                }
            }

            Divider()

            row("Subtotal", data.subtotal.format())
            if data.discountAmount.isPositive {
                row("Discount", "-\(data.discountAmount.format())")
            }
            row("Total", data.total.format(), emphasized: true)

            Divider()

            if data.cashAmount.isPositive { row("Cash", data.cashAmount.format()) }
            if data.upiAmount.isPositive { row("UPI", data.upiAmount.format()) }
            if data.dueAmount.isPositive { row("Due", data.dueAmount.format()) }

            if let remarks = data.remarks, !remarks.isEmpty {
                Divider()
                Text(remarks).font(.system(size: 10))
            }

            Divider()

            Text("Thank you!")
                .font(.system(size: 12, weight: .semibold))
                .frame(maxWidth: .infinity, alignment: .center)
        }
        .foregroundStyle(.black)
        .padding(12)
        .frame(width: width)
        .background(Color.white)
    }

    private func row(_ label: String, _ value: String, emphasized: Bool = false) -> some View {
        HStack {
            Text(label).font(.system(size: emphasized ? 13 : 11, weight: emphasized ? .bold : .regular))
            Spacer()
            Text(value).font(.system(size: emphasized ? 13 : 11, weight: emphasized ? .bold : .regular))
        }
    }
}
