import Foundation

struct MultipartFile {
    let fieldName: String
    let filename: String
    let mimeType: String
    let data: Data
}

enum MultipartFormData {
    static func build(boundary: String, fields: [String: String], file: MultipartFile) -> Data {
        var body = Data()

        func append(_ string: String) {
            body.append(Data(string.utf8))
        }

        for (name, value) in fields {
            append("--\(boundary)\r\n")
            append("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n")
            append("\(value)\r\n")
        }

        append("--\(boundary)\r\n")
        append("Content-Disposition: form-data; name=\"\(file.fieldName)\"; filename=\"\(file.filename)\"\r\n")
        append("Content-Type: \(file.mimeType)\r\n\r\n")
        body.append(file.data)
        append("\r\n")

        append("--\(boundary)--\r\n")
        return body
    }
}
