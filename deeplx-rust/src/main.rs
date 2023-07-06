mod deepl_dto;
mod deeplx_dto;
mod deepl_client;
mod asyn_translate;

use axum::{
    routing::{get, post},
    http::StatusCode,
    response::IntoResponse,
    Json, Router,
};
use deepl_dto::ReqestData;
use serde::{Deserialize, Serialize};
use std::net::SocketAddr;

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt::init();

    let app = Router::new()
        .route("/", get(root))
        .route("/users", post(create_user))
        .route("/translate", post(translate));

    axum::Server::bind(&"0.0.0.0:3000".parse().unwrap())
        .serve(app.into_make_service())
        .await
        .unwrap();
}

// basic handler that responds with a static string
async fn root() -> &'static str {
    "Hello, World!"
}

async fn create_user(Json(payload): Json<CreateUser>,) -> (StatusCode, Json<User>) {
    let user = User {
        id: 1337,
        username: payload.username,
    };
    (StatusCode::CREATED, Json(user))
}

async fn translate(asyncId: String, Json(reqest_data): Json<deeplx_dto::RequestData>) -> (StatusCode, Json<deeplx_dto::ResponseData>) {
    let (is_single, source_lang, target_lang, translate_texts) = match reqest_data {
        deeplx_dto::RequestData::Single { source_lang, target_lang, text } => {
            (true, source_lang, target_lang, vec![text])
        },
        deeplx_dto::RequestData::Batch { source_lang, target_lang, texts} => {
            (false, source_lang, target_lang, texts)
        }
    };

    if translate_texts.iter().all(|&text| text.trim().is_empty()) {
        return (StatusCode::BAD_REQUEST, Json(deeplx_dto::ResponseData::Error { code: StatusCode::BAD_REQUEST.as_u16() as i32, message: "Text Is Empty".to_string() }))
    }

    if asyncId.is_empty() {
        let deepl_response = deepl_client::translate(&mut source_lang, &mut target_lang, translate_texts).await;
        match deepl_response {
            deepl_dto::ResponseError{ .. } => {},
            deepl_dto::ResponseResult { .. } => {}
        }
    } else {
        asyn_translate::async_translate(asyn_id, source_lang, target_lang, translate_texts, is_single);
    }
    (StatusCode::OK, Json(payload))    
}

// the input to our `create_user` handler
#[derive(Deserialize)]
struct CreateUser {
    username: String,
}

// the output to our `create_user` handler
#[derive(Serialize)]
struct User {
    id: u64,
    username: String,
}