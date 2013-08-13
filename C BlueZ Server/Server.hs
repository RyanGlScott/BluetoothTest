module Server where

import Foreign.C

appender :: CString -> IO CString
appender str = do
  z <- peekCString str
  newCString (z ++ " (and Haskell, too)")

foreign export ccall appender :: CString -> IO CString