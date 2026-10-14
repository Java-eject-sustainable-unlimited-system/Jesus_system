with Ada.Unchecked_Conversion;

package body Bytecode_VM is
   use type Ada.Streams.Stream_Element;
   use type Ada.Streams.Stream_Element_Offset;
   use type Interfaces.Integer_32;
   use type Interfaces.Unsigned_32;

   Stack_Capacity : constant := 256;

   subtype Stack_Index is Natural range 0 .. Stack_Capacity;
   type Operand_Stack is array (Positive range 1 .. Stack_Capacity)
     of Interfaces.Integer_32;

   function To_Unsigned is new Ada.Unchecked_Conversion
     (Interfaces.Integer_32, Interfaces.Unsigned_32);
   function To_Signed is new Ada.Unchecked_Conversion
     (Interfaces.Unsigned_32, Interfaces.Integer_32);

   function Execute
     (Code : Ada.Streams.Stream_Element_Array)
      return Interfaces.Integer_32
   is
      Stack : Operand_Stack;
      Top   : Stack_Index := 0;
      PC    : Ada.Streams.Stream_Element_Offset := Code'First;

      procedure Push (Value : Interfaces.Integer_32) is
      begin
         if Top = Stack_Capacity then
            raise Bytecode_Error with "operand stack overflow";
         end if;
         Top := Top + 1;
         Stack (Top) := Value;
      end Push;

      function Pop return Interfaces.Integer_32 is
         Value : Interfaces.Integer_32;
      begin
         if Top = 0 then
            raise Bytecode_Error with "operand stack underflow";
         end if;
         Value := Stack (Top);
         Top := Top - 1;
         return Value;
      end Pop;

      function Read_Byte return Ada.Streams.Stream_Element is
         Value : Ada.Streams.Stream_Element;
      begin
         if PC > Code'Last then
            raise Bytecode_Error with "truncated bytecode operand";
         end if;
         Value := Code (PC);
         PC := PC + 1;
         return Value;
      end Read_Byte;

      Opcode : Ada.Streams.Stream_Element;
      Left   : Interfaces.Integer_32;
      Right  : Interfaces.Integer_32;
   begin
      while PC <= Code'Last loop
         Opcode := Read_Byte;
         case Opcode is
            when 16#02# .. 16#08# =>
               Push (Interfaces.Integer_32 (Opcode) - 3);
            when 16#10# =>
               declare
                  Operand : constant Ada.Streams.Stream_Element :=
                    Read_Byte;
               begin
                  if Operand < 128 then
                     Push (Interfaces.Integer_32 (Operand));
                  else
                     Push (Interfaces.Integer_32 (Operand) - 256);
                  end if;
               end;
            when 16#60# =>
               Right := Pop;
               Left := Pop;
               Push (To_Signed (To_Unsigned (Left) + To_Unsigned (Right)));
            when 16#64# =>
               Right := Pop;
               Left := Pop;
               Push (To_Signed (To_Unsigned (Left) - To_Unsigned (Right)));
            when 16#68# =>
               Right := Pop;
               Left := Pop;
               Push (To_Signed (To_Unsigned (Left) * To_Unsigned (Right)));
            when 16#6C# =>
               Right := Pop;
               Left := Pop;
               if Right = 0 then
                  raise Bytecode_Error with "integer division by zero";
               elsif Left = Interfaces.Integer_32'First
                 and then Right = -1
               then
                  Push (Interfaces.Integer_32'First);
               else
                  Push (Left / Right);
               end if;
            when 16#AC# =>
               if Top = 0 then
                  raise Bytecode_Error with "ireturn requires a stack value";
               end if;
               return Pop;
            when others =>
               raise Bytecode_Error with
                 "unsupported opcode" & Ada.Streams.Stream_Element'Image
                   (Opcode);
         end case;
      end loop;

      raise Bytecode_Error with "method bytecode ended without ireturn";
   end Execute;
end Bytecode_VM;
