dependencies {
    implementation(project(":core"))
    // The system keychains (Keychain, Credential Manager, Secret Service) through their native APIs
    implementation("net.java.dev.jna:jna:5.19.1")
}
