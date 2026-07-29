import SwiftUI

struct LanguagePickerView: View {
    @Environment(LanguageStore.self) private var languageStore
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List(AppLanguage.allCases) { language in
                Button {
                    languageStore.set(language)
                    dismiss()
                } label: {
                    HStack {
                        Text(language.nativeName)
                            .font(PlantbillTypography.body)
                            .fontWeight(.medium)
                            .foregroundStyle(PlantbillColor.textPrimary)
                        Spacer()
                        if language == languageStore.current {
                            Image(systemName: "checkmark")
                                .foregroundStyle(PlantbillColor.green)
                        }
                    }
                    .frame(minHeight: PlantbillSpacing.minTouchTarget)
                    .contentShape(Rectangle())
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Language")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

#Preview {
    LanguagePickerView()
        .environment(LanguageStore())
}
