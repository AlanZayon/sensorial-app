// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "Sensorial",
    platforms: [
        .iOS(.v17),
        .macOS(.v14),
    ],
    products: [
        .library(name: "SensorialDomain", targets: ["SensorialDomain"]),
        .library(name: "SensorialData", targets: ["SensorialData"]),
    ],
    targets: [
        .target(name: "SensorialDomain"),
        .target(
            name: "SensorialData",
            dependencies: ["SensorialDomain"]
        ),
        .testTarget(
            name: "SensorialDomainTests",
            dependencies: ["SensorialDomain", "SensorialData"]
        ),
    ]
)
