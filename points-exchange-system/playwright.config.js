const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./e2e",
  timeout: 90_000,
  expect: {
    timeout: 15_000
  },
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: "http://127.0.0.1:8099",
    viewport: { width: 390, height: 844 },
    trace: "retain-on-failure"
  },
  webServer: {
    command:
      'bash -lc \'export JAVA_HOME=$(/usr/libexec/java_home -v 11); export PATH="$JAVA_HOME/bin:$PATH"; export PORT=8099; export SPRING_DATASOURCE_URL="jdbc:h2:mem:pw;MODE=MYSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"; export SPRING_JPA_HIBERNATE_DDL_AUTO=create-drop; mvn -q -DskipTests spring-boot:run\'',
    url: "http://127.0.0.1:8099/app/login.html",
    timeout: 120_000,
    reuseExistingServer: true
  }
});
