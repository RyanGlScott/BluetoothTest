{-# LANGUAGE ForeignFunctionInterface #-}
module BluezServer where
import Foreign.C

-- foreign import ccall "run_server" run_server :: IO ()
foreign import ccall "init_server" init_server :: IO CInt
foreign import ccall "read_server" read_server :: CInt -> CString
foreign import ccall "write_server" write_server :: CInt -> CString -> IO ()