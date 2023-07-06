use serde::{Deserialize, Serialize};


#[derive(Deserialize)]
#[serde (tag = "type")]
pub enum RequestData {
    #[serde (rename = "single")]
    Single {
        source_lang: String,
        target_lang: String,
        text: String,
    },
    #[serde (rename = "batch")]
    Batch {
        source_lang: String,
        target_lang: String,
        texts: Vec<String>,
    }
}

#[derive(Serialize)]
#[serde (untagged)]
pub enum ResponseData {
    Single {
        code: i32,
        id: i64,
        data: String,
        alternatives: Vec<String>,
    },

    Batch {
        code: i32,
        id: i64,
        datas: Vec<BatchData>,
    },

    Error {
        code: i32,
        message: String,
    },
}

#[derive(Serialize)]
pub struct BatchData {
    pub data: String,
    pub alternatives: Vec<String>
}
