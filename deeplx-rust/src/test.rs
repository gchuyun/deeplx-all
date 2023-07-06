use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use std::time::Duration;
use hyper::{Body, Request, Response, Server};
use hyper::service::{make_service_fn, service_fn};
use serde_json::{Value, json};
use reqwest::Client;

// 定义一个结构体来存储异步请求的数据
struct AsyncTranslateRequest {
    asyn_id: String,
    body: Value,
    sender: futures::channel::oneshot::Sender<Response<Body>>,
}

// 定义一个结构体来存储合并后的请求的数据
struct MergedRequest {
    asyn_id: String,
    body: Vec<Value>,
    senders: Vec<futures::channel::oneshot::Sender<Response<Body>>>,
}

// 定义一个全局变量来存储异步请求的队列
lazy_static! {
    static ref QUEUE: Arc<Mutex<Vec<AsyncTranslateRequest>>> = Arc::new(Mutex::new(Vec::new()));
}

// 定义一个函数来处理http请求
async fn handle_request(req: Request<Body>) -> Result<Response<Body>, hyper::Error> {
    // 判断请求的路径是否是/translate，并且是否有asynId参数
    if let Some(path) = req.uri().path_and_query() {
        if path.as_str().starts_with("/translate") {
            if let Some(query) = path.query() {
                let params: HashMap<_, _> = query.split("&").map(|s| s.split("=").collect::<Vec<_>>()).map(|v| (v[0], v[1])).collect();
                if let Some(asyn_id) = params.get("asynId") {
                    // 获取请求的json数据
                    let data = hyper::body::to_bytes(req.into_body()).await?;
                    let json_data: Value = serde_json::from_slice(&data).unwrap();
                    // 创建一个通道来发送响应
                    let (sender, receiver) = futures::channel::oneshot::channel();
                    // 创建一个异步请求对象
                    let async_req = AsyncTranslateRequest {
                        asyn_id: asyn_id.to_string(),
                        body: json_data,
                        sender,
                    };
                    // 将异步请求对象加入到队列中
                    QUEUE.lock().unwrap().push(async_req);
                    // 等待接收响应
                    let resp = receiver.await.unwrap();
                    return Ok(resp);
                }
            }
        }
    }
    // 如果不符合条件，返回400错误
    Ok(Response::builder()
        .status(400)
        .body("Bad request".into())
        .unwrap())
}

// 定义一个函数来合并队列中的异步请求，并向deepl.com发送翻译请求

#[tokio::main]
async fn main() {
    // 创建一个http服务，监听8080端口，使用handle_request函数处理请求
    let addr = ([127, 0, 0, 1], 8080).into();
    let make_svc = make_service_fn(|_conn| async {
        Ok::<_, hyper::Error>(service_fn(handle_request))
    });
    let server = Server::bind(&addr).serve(make_svc);

    // 创建一个任务，使用merge_and_translate函数合并队列中的异步请求，并向deepl.com发送翻译请求
    let task = tokio::spawn(async {
        merge_and_translate().await;
    });

    // 等待两个任务完成
    if let Err(e) = server.await {
        eprintln!("server error: {}", e);
    }
    if let Err(e) = task.await {
        eprintln!("task error: {}", e);
    }
}