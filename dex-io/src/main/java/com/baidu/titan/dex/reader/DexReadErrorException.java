/*
 * Copyright (C) Baidu Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.baidu.titan.dex.reader;

/**
 * Dex读取失败异常
 *
 * @author zhangdi07@baidu.com
 * @since 2017/2/6
 */
public class DexReadErrorException extends RuntimeException {
    public DexReadErrorException() {
    }

    public DexReadErrorException(String message) {
        super(message);
    }

    public DexReadErrorException(String message, Throwable cause) {
        super(message, cause);
    }

    public DexReadErrorException(Throwable cause) {
        super(null, cause);
    }

    public DexReadErrorException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
