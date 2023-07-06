use std::collections::HashMap;

use serde::{Deserialize, Serialize};
use nested_struct::nested_struct;

nested_struct! {
    @nested(#[derive(Serialize)])
    pub struct ReqestData {
        jsonrpc: String,
        method: String,
        id: i64,
        params: Vec<Param> : (Param {
            texts: Vec<Text> : (Text {
                text: String,
                requestAlternatives: i32,
            }),
            splitting: String,
            lang: Lang : (Lang {
                source_lang_user_selected: String,
                target_lang: String,
            }),
            timestamp: i64,
            commonJobParams: JobParams : (JobParams {
                wasSpoken: bool,
                transcribe_as: String,
                regionalVariant: String,
            }),
        })
    }
}

nested_struct! {
    @nested(#[derive(Deserialize)])
    pub struct ResponseResult {
        jsonrpc: String,
        id: i64,
        result: Result : (Result {
            lang: String,
            lang_is_confident: bool,
            detectedLanguages: HashMap<String, f32>,
            texts: Vec<TText> : (TText {
                text: String,
                alternatives: Vec<Alternative> : (Alternative {
                    text: String,
                }),
            }),
        }),
    }
}

nested_struct! {
    @nested(#[derive(Deserialize)])
    pub struct ResponseError {
        jsonrpc: String,
        id: i64,
        error: ErrData : (ErrData {
            code: i64,
            message: String,
            data: Reson : (Reson {
                what: String,
            }),
        }),
    }
}