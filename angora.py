import requests
from bs4 import BeautifulSoup
import json
import os
import re

# === CONFIG ===
WEBHOOK_URL = "https://discord.com/api/webhooks/1363606767765819503/ahU60WWHSOaQI29nghwEZF8hPZyXIkvcwIlRY9ffOIbP6zsAWFuNA-ueYzsXbs8q9L8C"
SEARCH_URL = "https://www.olx.com.br/animais-e-acessorios/gatos?q=angor%C3%A1&region=piracicaba"
HEADERS = {"User-Agent": "Mozilla/5.0"}
CACHE_FILE = "sent_ads.json"

# === HELPERS ===
def load_cache():
    if os.path.exists(CACHE_FILE):
        with open(CACHE_FILE, "r") as f:
            return set(json.load(f))
    return set()

def save_cache(cache):
    with open(CACHE_FILE, "w") as f:
        json.dump(list(cache), f)

def extract_price(text: str) -> float:
    match = re.search(r"R\$\s*([\d.,]+)", text)
    if not match:
        return float("inf")
    price_str = match.group(1).replace(".", "").replace(",", ".")
    try:
        return float(price_str)
    except ValueError:
        return float("inf")

def send_discord_notification(title, link, price):
    data = {
        "content": f"🐱 **Novo gato Angorá abaixo de R$300!**\n**{title}**\n💰 R${price:.2f}\n🔗 {link}"
    }
    requests.post(WEBHOOK_URL, json=data)

# === MAIN FUNCTION ===
def check_ads():
    print("Checking for new ads...")
    already_sent = load_cache()
    response = requests.get(SEARCH_URL, headers=HEADERS)
    soup = BeautifulSoup(response.text, "html.parser")
    ads = soup.find_all("li", class_="sc-1fcmfeb-2")

    for ad in ads:
        a_tag = ad.find("a")
        if not a_tag:
            continue
        link = a_tag["href"]
        title = ad.get_text().strip()
        print(title)

        price = extract_price(ad.get_text())

        if "angorá" in title.lower() and price < 3000 and link not in already_sent:
            send_discord_notification(title, link, price)
            already_sent.add(link)

    save_cache(already_sent)

if __name__ == "__main__":
    check_ads()
