const { test, expect } = require("@playwright/test");
const path = require("path");
const fs = require("fs");

async function loginUser(page, options = {}) {
  const {
    displayName = "演示用户",
    phoneNumber = "13800000000",
    hrCode = "HR0001"
  } = options;
  await page.goto("/app/login.html");
  await page.locator("#displayName").fill(displayName);
  await page.locator("#phoneNumber").fill(phoneNumber);
  await page.locator("#hrCode").fill(hrCode);
  await page.locator("#sendCodeBtn").click();
  await expect(page.locator("#debugCode")).toContainText("开发环境验证码");
  const text = await page.locator("#debugCode").innerText();
  const match = text.match(/(\d{6})/);
  if (!match) {
    throw new Error("未获取到验证码");
  }
  await page.locator("#smsCode").fill(match[1]);
  await page.locator("#loginBtn").click();
  await page.waitForURL("**/app/index.html");
}

test("用户下单流程（UI）", async ({ page }) => {
  page.on("dialog", (dialog) => dialog.accept());

  await loginUser(page);
  await expect(page.locator("#welcomeText")).toContainText("演示用户");

  const firstCard = page.locator("#itemGrid .plan-card").first();
  await expect(firstCard).toBeVisible();
  const itemName = (await firstCard.locator("h3").innerText()).trim();

  await firstCard.getByRole("button", { name: "选择此方案" }).click();
  await page.locator("#redeemRecipient").fill("张三");
  await page.locator("#redeemPhone").fill("13800000000");
  await page.locator("#redeemAddress").fill("上海市浦东新区测试地址 1 号");
  await page.locator("#submitRedeemBtn").click();

  await page.getByRole("button", { name: "订单" }).click();
  await expect(page.locator("#orderList")).toContainText(itemName);
});

test("管理员发货后用户能看到物流入口（UI）", async ({ page, browser }) => {
  page.on("dialog", (dialog) => dialog.accept());

  const adminContext = await browser.newContext();
  const adminPage = await adminContext.newPage();
  await adminPage.goto("/admin/login.html");
  await adminPage.locator("#loginBtn").click();
  await adminPage.waitForURL("**/admin/index.html");

  await adminPage.evaluate(async () => {
    const formData = new FormData();
    const csv = "姓名,手机号,人力资源码\n测试物流用户,13900000002,HR9002\n";
    formData.append("file", new File([csv], "users.csv", { type: "text/csv" }));
    const response = await fetch("/api/admin/users/import-csv", {
      method: "POST",
      credentials: "include",
      body: formData
    });
    if (!response.ok) {
      throw new Error("导入测试用户失败");
    }
  });

  await loginUser(page, {
    displayName: "测试物流用户",
    phoneNumber: "13900000002",
    hrCode: "HR9002"
  });

  const firstCard = page.locator("#itemGrid .plan-card").first();
  const itemName = (await firstCard.locator("h3").innerText()).trim();
  await firstCard.getByRole("button", { name: "选择此方案" }).click();
  await page.locator("#redeemRecipient").fill("张三");
  await page.locator("#redeemPhone").fill("13800000000");
  await page.locator("#redeemAddress").fill("上海市浦东新区测试地址 1 号");
  await page.locator("#submitRedeemBtn").click();

  await page.getByRole("button", { name: "订单" }).click();
  await expect(page.locator("#orderList")).toContainText(itemName);

  const orderNo = (await page.locator("#orderList .order-card").first().locator(".order-meta").first().innerText()).replace("订单号：", "").trim();
  expect(orderNo.length).toBeGreaterThan(0);

  const dialogHandler = async (dialog) => {
    if (dialog.type() !== "prompt") {
      await dialog.accept();
      return;
    }
    const msg = dialog.message() || "";
    if (msg.includes("快递单号")) {
      await dialog.accept("YT123456789");
      return;
    }
    if (msg.includes("快递公司编码")) {
      await dialog.accept("yuantong");
      return;
    }
    await dialog.accept("");
  };
  adminPage.on("dialog", dialogHandler);

  await adminPage.goto("/admin/login.html");
  await adminPage.locator("#loginBtn").click();
  await adminPage.waitForURL("**/admin/index.html");

  await expect(adminPage.locator("#orderTable")).toContainText(orderNo);
  const row = adminPage.locator("#orderTable tr", { hasText: orderNo });
  await row.getByRole("button", { name: "发货" }).click();

  await expect(row.locator("td").nth(3)).not.toHaveText("CREATED");
  await expect(row).toContainText("YT123456789");

  await adminContext.close();

  await page.reload();
  await page.getByRole("button", { name: "订单" }).click();
  const userRow = page.locator("#orderList .order-card", { hasText: orderNo });
  await expect(userRow.getByRole("button", { name: "查看物流" })).toBeVisible();
});

test("后台导入用户、配置公告、导出报表（UI）", async ({ page }) => {
  await page.goto("/admin/login.html");
  await page.locator("#loginBtn").click();
  await page.waitForURL("**/admin/index.html");

  const csvPath = path.join(__dirname, "fixtures", "users.csv");
  await page.locator("#userImportFile").setInputFiles(csvPath);
  await page.locator("#importUsersBtn").click();
  await expect(page.locator("#userTable")).toContainText("未兑换用户");

  await page.locator("#announcementHtml").fill("<p><strong>测试公告</strong></p><p><a href=\"https://example.com\">查看详情</a></p>");
  await page.locator("#hotline").fill("400-800-0000");
  await page.locator("#saveSiteConfigBtn").click();
  await expect(page.locator("#announcementPreview")).toContainText("测试公告");

  const downloadPromise = page.waitForEvent("download");
  await page.locator("#exportRedemptionReportBtn").click();
  const download = await downloadPromise;
  const filePath = await download.path();
  expect(download.suggestedFilename()).toMatch(/redemption-report-\d{8}\.xlsx/);
  if (filePath) {
    const buf = fs.readFileSync(filePath);
    expect(buf.subarray(0, 2).toString("utf8")).toBe("PK");
  }
});
