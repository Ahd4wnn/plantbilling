import SwiftUI

struct MoreView: View {
    let user: CurrentUser

    @Environment(AuthSession.self) private var session
    @Environment(LanguageStore.self) private var languageStore
    @State private var showingLanguagePicker = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    userCard
                }
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())

                Section {
                    Button {
                        showingLanguagePicker = true
                    } label: {
                        settingsRow(icon: "globe", title: "Language", value: Text(languageStore.current.nativeName))
                    }
                }

                Section {
                    Button(role: .destructive) {
                        session.logout()
                    } label: {
                        Text("Log out")
                            .font(PlantbillTypography.bodyEmphasized)
                            .frame(maxWidth: .infinity)
                            .contentShape(Rectangle())
                    }
                }

                Section {
                    HStack {
                        Spacer()
                        Text(versionString)
                            .font(PlantbillTypography.caption)
                            .foregroundStyle(PlantbillColor.textSecondary)
                        Spacer()
                    }
                }
                .listRowBackground(Color.clear)
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(PlantbillColor.background)
            .navigationTitle("More")
            .notificationBell()
            .sheet(isPresented: $showingLanguagePicker) {
                LanguagePickerView()
            }
        }
    }

    private var userCard: some View {
        PlantbillCard {
            VStack(alignment: .leading, spacing: PlantbillSpacing.xs) {
                if let shopName = user.shopName, !shopName.isEmpty {
                    Text(shopName)
                        .font(PlantbillTypography.headline)
                        .foregroundStyle(PlantbillColor.textPrimary)
                }
                Text(user.email)
                    .font(PlantbillTypography.body)
                    .foregroundStyle(PlantbillColor.textSecondary)
                Text(user.role.displayName)
                    .font(PlantbillTypography.caption)
                    .foregroundStyle(PlantbillColor.green)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, PlantbillSpacing.md)
        .padding(.top, PlantbillSpacing.sm)
    }

    @ViewBuilder
    private func settingsRow(icon: String, title: LocalizedStringKey, value: Text?) -> some View {
        HStack {
            Image(systemName: icon)
                .foregroundStyle(PlantbillColor.green)
                .frame(width: 28)
            Text(title)
                .font(PlantbillTypography.body)
                .fontWeight(.medium)
                .foregroundStyle(PlantbillColor.textPrimary)
            Spacer()
            if let value {
                value
                    .font(PlantbillTypography.body)
                    .foregroundStyle(PlantbillColor.textSecondary)
            }
            Image(systemName: "chevron.right")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(PlantbillColor.textSecondary)
        }
        .frame(minHeight: PlantbillSpacing.minTouchTarget)
        .contentShape(Rectangle())
    }

    private var versionString: String {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
        return "Plantbill v\(version) (\(build))"
    }
}

#Preview {
    MoreView(user: CurrentUser(id: UUID(), email: "owner@example.com", role: .manager, shopId: UUID(), isActive: true, shopName: "Green Leaf Nursery", businessName: nil, businessUpi: nil))
        .environment(AuthSession())
        .environment(LanguageStore())
        .environment(NotificationsStore())
}
