mod deepl_client;

struct AsyncTranslateRequest {
    asyn_id: String,
    source_lang: String,
    target_lang: String,
    trans_texts: Vec<String>,
    is_single: bool,
    sender: futures::channel::oneshot::Sender<Response<Body>>,
}

lazy_static! {
    static ref ASYN_TRANSLATE_QUEUE: Arc<Mutex<Vec<AsyncTranslateRequest>>> =
        Arc::new(Mutex::new(Vec::new()));
}

pub fn async_translate(
    asyn_id: String,
    source_lang: String,
    target_lang: String,
    trans_texts: Vec<String>,
    is_single: bool,
) -> futures::channel::oneshot::Receiver<Response<Body>> {
    let (sender, receiver) = futures::channel::oneshot::channel();
    QUEUE.lock().unwrap().push(AsyncTranslateRequest {
        asyn_id,
        source_lang,
        target_lang,
        trans_texts,
        is_single,
        sender,
    });
    receiver
}

pub async fn merge_and_translate() {
    // 创建一个循环，每隔一秒执行一次
    loop {
        tokio::time::sleep(Duration::from_secs(1)).await;

        let mut queue = ASYN_TRANSLATE_QUEUE.lock().unwrap();
        // 获取队列中第一个元素的asynId
        let first_asyn_id = queue.first().map(|async_req| async_req.asyn_id.clone());
        // 如果队列不为空，取出和第一个元素相同asynId的元素，保留其他元素
        let mut one_batch_async_reqs = Vec::new();
        if let Some(asyn_id) = first_asyn_id {
            one_batch_async_reqs = queue
                .drain_filter(|async_req| async_req.asyn_id == asyn_id)
                .collect();
        }
        drop(queue);

        // 如果队列为空，跳过本次循环
        if one_batch_async_reqs.is_empty() {
            continue;
        }

        // 获取第一个job的源语言和目标语言
        let source_lang = one_batch_async_reqs[0].source_lang();
        let target_lang = one_batch_async_reqs[0].target_lang();
        // 获取所有job的待翻译文本，并放入一个Vec<String>中
        let trans_texts: Vec<String> = one_batch_async_reqs
            .iter()
            .map(|req| req.trans_texts)
            .flatten()
            .collect();
        // 调用deep_l_client的translate方法，并处理结果
        deepl_client
            .translate(source_lang, target_lang, trans_texts)
            .do_on_error(|throwable| {
                // 如果出错，让每个job的response_future抛出异常
                for req in &one_batch_async_reqs {
                    req.sender.send("error").unwrap();
                }
            })
            .subscribe(|deepl_resp_data| {
                // 如果成功，根据每个job的大小，从deep_l_resp_data中提取对应的response_data，并完成每个job的response_future
                let mut begin_index = 0;
                for req in &one_batch_async_reqs {
                    let size = if req.is_single {
                        1
                    } else {
                        req.trans_texts().len()
                    };
                    let end_index = begin_index + size;
                    let response_data = extract_deep_lx_response_data(
                        deepl_resp_data,
                        begin_index,
                        end_index,
                        req.is_single,
                    );
                    req.sender.send(response_data).unwrap();
                    begin_index = end_index;
                }
            });
    }
}
