module ServerEx where
import qualified BluezServer
import Foreign.C

main = do
  client <- BluezServer.init_server
  loopServer client
  
loopServer :: CInt -> IO ()
loopServer client = do
  message <- peekCString $ BluezServer.read_server client
  response <- newCString $ hermitMagic message
  if (not $ null message) then do
    BluezServer.write_server client response
    loopServer client
			  else return ()

hermitMagic :: [Char] -> [Char]
-- Replace this with some other crazy string manipulation
hermitMagic str = str ++ " (don't forget Haskell!)"