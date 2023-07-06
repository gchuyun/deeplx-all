use hyper::{Body, Client, Method, Request};
use hyper_tls::HttpsConnector;
use rand::Rng;
use regex::Regex;
use std::time::SystemTime;
use tokio::io::AsyncWriteExt;

lazy_static! {
    static REGEX_HAN: Regex = Regex::new(r".*[\u4E00-\u9FA5]+.*").unwrap();
    static CLIENT = Client::builder().build::<_, Body>(HttpsConnector::new());
}

pub async fn translate(source_lang: &mut String, target_lang: &mut String, translate_texts: Vec<String>) {
    if source_lang.trim().is_empty() {
        source_lang = "AUTO";
    }
    if target_lang.trim().is_empty() {
        if translate_texts.iter().any(|&text| REGEX_HAN.is_match(text)) {
            target_lang = "EN";
        } else {
            target_lang = "ZH";
        }
    }

    let id = get_random_id();
    let data = deepl_dto::RequestData {
        jsonrpc: "2.0",
        method: "LMT_handle_texts",
        id: id,
        params: vec![Params {
            splitting: "newlines",
            lang: Lang {
                source_lang: source_lang,
                target_lang: target_lang,
            },
            commonJobParams: CommonJobParams {
                wasSpoken: false,
                transcribe_as: "",
            },
            texts: translate_texts.map(|t| Text {
                text: t,
                requestAlternatives: 3,
            }),
            timestamp: get_i_count_timestamp(translate_texts.map(|t| get_i_count(t).sum())),
        }],
    };

    let post_str: &String = serde_json::to_string(&data)?;
    if ((id + 5) % 29 == 0 || (id + 3) % 13 == 0) {
        post_str = post_str.replace("\"method\":\"", "\"method\" : \"");
    } else {
        post_str = post_str.replace("\"method\":\"", "\"method\": \"");
    }
    println!("deepl request: {}", &post_str);

    
    let request = Request::builder()
        .method(Method::POST)
        .uri("https://www2.deepl.com/jsonrpc")
        .header("Content-Type", "application/json")
        .header("Accept", "*/*")
        .header("x-app-os-name", "iOS")
        .header("x-app-os-version", "16.3.0")
        .header("Accept-Language", "en-US,en;q=0.9")
        .header("Accept-Encoding", "gzip, deflate, br")
        .header("x-app-device", "iPhone13,2")
        .header("User-Agent", "DeepL-iOS/2.6.0 iOS 16.3.0 (iPhone13,2)")
        .header("x-app-build", "353933")
        .header("x-app-version", "2.6")
        .header("Connection", "keep-alive")
        .body(post_str.into())
        .unwrap();

    let response = CLIENT.request(request).await.unwrap();
    let status = response.status();
    let body = hyper::body::to_bytes(response.into_body()).await.unwrap();
    println!("deepl response: {}", &body);
    if status.is_client_error() || status.is_server_error() {
        let error_data: deepl_dto::Error = serde_json::from_slice(&body).unwrap();
        println!("deepl request: {}", post_str);
        println!("deepl error: {}", to_string(&error_data).unwrap());
        error_data.set_http_status_code(status.as_u16());
    } else {
        let result_data: deepl_dto::Result = from_slice(&body).unwrap();
    }
}

fn init_data(src_lang: String, tgt_lang: String) -> deepl_dto::RequestData {
    deepl_dto::RequestData {
        jsonrpc: "2.0",
        method: "LMT_handle_texts",
        params: vec![Params {
            splitting: "newlines",
            lang: Lang {
                source_lang: src_lang,
                target_lang: tgt_lang,
            },
            commonJobParams: CommonJobParams {
                wasSpoken: false,
                transcribe_as: "",
            },
        }],
    }
}

fn get_i_count(translate_text: &String) -> i64 {
    translate_text.chars().filter(|ch| ch == 'i').count();
}

fn get_random_id() -> i64 {
    rand::thread_rng().gen_range(8300000..8339999) * 1000;
}

fn get_i_count_timestamp(i_count: i64) -> i64 {
    let time_millis = SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .unwrap()
        .as_millis() as i64;
    if i_count != 0 {
        i_count = i_count + 1;
        return time_millis - time_millis % i_count + i_count;
    } else {
        return time_millis;
    }
}
